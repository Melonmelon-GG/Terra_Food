#!/usr/bin/env python3
"""Manual consistent DB snapshot + immutable upload copy, encrypted before publication.

Requires a private passphrase file outside the repository and an operator-owned
destination. Never changes live data or installs a backup schedule.
"""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time

AUTH_MAGIC=b'TFBAK1\0\0'
AUTH_FOOTER_SIZE=len(AUTH_MAGIC)+64

def authentication_key(passphrase_file, salt):
    # Match OpenSSL's file: password source (first line), using an independent salt/key.
    password=passphrase_file.read_bytes().splitlines()[0]
    return hashlib.pbkdf2_hmac('sha256',password,salt,600000,dklen=32)

def ciphertext_mac(path,key,length,salt):
    mac=hmac.new(key,digestmod='sha256')
    with path.open('rb') as stream:
        remaining=length
        while remaining:
            chunk=stream.read(min(1024*1024,remaining))
            if not chunk: raise ValueError('truncated backup')
            mac.update(chunk);remaining-=len(chunk)
    mac.update(AUTH_MAGIC+salt)
    return mac.digest()

def seal_archive(path,passphrase_file):
    salt=os.urandom(32)
    tag=ciphertext_mac(path,authentication_key(passphrase_file,salt),path.stat().st_size,salt)
    with path.open('ab') as stream:
        stream.write(AUTH_MAGIC+salt+tag);stream.flush();os.fsync(stream.fileno())

def verify_archive(path,passphrase_file):
    length=path.stat().st_size-AUTH_FOOTER_SIZE
    if length<=0: raise ValueError('backup envelope missing')
    with path.open('rb') as stream:
        stream.seek(length);footer=stream.read(AUTH_FOOTER_SIZE)
    if not footer.startswith(AUTH_MAGIC): raise ValueError('unknown backup envelope')
    salt=footer[len(AUTH_MAGIC):len(AUTH_MAGIC)+32]
    expected=ciphertext_mac(path,authentication_key(passphrase_file,salt),length,salt)
    if not hmac.compare_digest(expected,footer[-32:]): raise ValueError('backup authentication failed')
    return length

def digest_file(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        while chunk:=stream.read(1024*1024): digest.update(chunk)
    return digest.hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--mysql-container',required=True)
    parser.add_argument('--upload-container',required=True)
    parser.add_argument('--upload-path',default='/data/uploads')
    parser.add_argument('--passphrase-file',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.passphrase_file.stat().st_mode & 0o077 or not args.passphrase_file.is_file() or not 16 <= args.passphrase_file.stat().st_size <= 1024: raise ValueError('passphrase file must be private, regular, and contain 16..1024 bytes')
    if args.output.exists(): raise ValueError('refusing to overwrite existing backup')
    os.umask(0o077)
    args.output.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    if args.output.parent.stat().st_mode & 0o077: raise ValueError("backup directory must be private (0700)")
    docker=['/snap/docker/current/bin/docker','--host','unix:///var/run/docker.sock'] if Path('/snap/docker/current/bin/docker').exists() else ['docker']
    with tempfile.TemporaryDirectory(prefix='terra-backup-') as name:
        root=Path(name)
        with (root/'database.sql').open('wb') as stream:
            subprocess.run(docker+['exec',args.mysql_container,'sh','-c',
                'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -h127.0.0.1 -u "$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"'],stdout=stream,check=True,timeout=300)
        subprocess.run(docker+['cp',args.upload_container+':'+args.upload_path,str(root/'uploads')],check=True,timeout=300)
        metadata={'schema':1,'created_at':time.time(),'database_sha256':digest_file(root/'database.sql'),
                  'uploads_semantics':'originals immutable; automatic deletion must remain disabled during snapshot'}
        (root/'manifest.json').write_text(json.dumps(metadata,indent=2))
        fd, temporary=tempfile.mkstemp(prefix='.terra-backup-',suffix='.partial',dir=args.output.parent)
        os.close(fd)
        partial=Path(temporary)
        encryption=None
        try:
            encryption=subprocess.Popen(['openssl','enc','-aes-256-cbc','-salt','-pbkdf2','-iter','600000','-pass','file:'+str(args.passphrase_file),'-out',str(partial)],stdin=subprocess.PIPE)
            with tarfile.open(fileobj=encryption.stdin,mode='w|gz') as archive:
                for child in root.iterdir(): archive.add(child,arcname=child.name)
            encryption.stdin.close()
            if encryption.wait(timeout=120): raise RuntimeError('backup encryption failed')
            seal_archive(partial,args.passphrase_file)
            digest=digest_file(partial)
            os.link(partial,args.output)
            partial.unlink()
            args.output.with_suffix(args.output.suffix+'.sha256').write_text(digest+'  '+args.output.name+'\n')
        finally:
            if encryption is not None and encryption.poll() is None:
                encryption.kill(); encryption.wait(timeout=10)
            partial.unlink(missing_ok=True)
    print(json.dumps({'output':str(args.output),'sha256':digest,'restore_verified':False}))

if __name__=='__main__': main()
