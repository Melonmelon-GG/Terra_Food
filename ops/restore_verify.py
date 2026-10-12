#!/usr/bin/env python3
"""Authenticate and unpack a backup for an isolated restore drill; never imports into a DB."""
import argparse
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import shutil
from backup import verify_archive

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--backup',type=Path,required=True)
    parser.add_argument('--passphrase-file',type=Path,required=True)
    parser.add_argument('--output-directory',type=Path,required=True)
    args=parser.parse_args()
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='terra-restore-') as temporary:
        # Authenticate a private snapshot so replacing/changing the source afterwards
        # cannot turn a previously authenticated path into different decrypted bytes.
        snapshot=Path(temporary)/'encrypted'
        shutil.copyfile(args.backup,snapshot)
        length=verify_archive(snapshot,args.passphrase_file)
        # Existing output is never merged or replaced by a restore.
        args.output_directory.mkdir(mode=0o700)
        plain=Path(temporary)/'archive.tar.gz'
        process=subprocess.Popen(['openssl','enc','-d','-aes-256-cbc','-pbkdf2','-iter','600000',
            '-pass','file:'+str(args.passphrase_file),'-out',str(plain)],stdin=subprocess.PIPE)
        try:
            with snapshot.open('rb') as stream:
                while length:
                    chunk=stream.read(min(length,1024*1024));process.stdin.write(chunk);length-=len(chunk)
            process.stdin.close()
            if process.wait(timeout=120): raise RuntimeError('decryption failed')
            with tarfile.open(plain) as archive: archive.extractall(args.output_directory,filter='data')
        finally:
            if process.poll() is None: process.kill();process.wait(timeout=10)
    print('Authenticated backup extracted; database restore and application verification still required.')

if __name__=='__main__': main()
