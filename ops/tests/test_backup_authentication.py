import importlib.util
from pathlib import Path
import tempfile
import unittest
import subprocess
import tarfile
import shutil

spec=importlib.util.spec_from_file_location('backup_auth',Path(__file__).parents[1]/'backup.py')
backup=importlib.util.module_from_spec(spec);spec.loader.exec_module(backup)

class BackupAuthentication(unittest.TestCase):
    @unittest.skipUnless(shutil.which('openssl'),'OpenSSL required for real envelope restore')
    def test_real_encryption_and_authenticated_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);password=root/'password';password.write_bytes(b'fixture-password-only\n')
            payload=root/'database.sql';payload.write_text('SELECT 1;\n')
            plain=root/'plain.tar.gz';encrypted=root/'backup.enc'
            with tarfile.open(plain,'w:gz') as archive:archive.add(payload,arcname='database.sql')
            subprocess.run(['openssl','enc','-aes-256-cbc','-salt','-pbkdf2','-iter','600000',
                '-pass','file:'+str(password),'-in',str(plain),'-out',str(encrypted)],check=True)
            backup.seal_archive(encrypted,password)
            subprocess.run([__import__('sys').executable,str(Path(__file__).parents[1]/'restore_verify.py'),
                '--backup',str(encrypted),'--passphrase-file',str(password),'--output-directory',str(root/'restore')],check=True,capture_output=True)
            self.assertEqual((root/'restore/database.sql').read_text(),payload.read_text())
    def test_authenticate_before_decrypt_rejects_tampering_and_wrong_password(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);cipher=root/'backup';password=root/'password'
            password.write_bytes(b'fixture-password-only\n');cipher.write_bytes(b'opaque-ciphertext')
            backup.seal_archive(cipher,password)
            self.assertEqual(backup.verify_archive(cipher,password),len(b'opaque-ciphertext'))
            password.write_bytes(b'other-fixture-password\n')
            with self.assertRaises(ValueError):backup.verify_archive(cipher,password)
            password.write_bytes(b'fixture-password-only\n')
            with cipher.open('r+b') as stream:stream.write(b'X')
            with self.assertRaises(ValueError):backup.verify_archive(cipher,password)

    def test_legacy_unauthenticated_ciphertext_is_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);cipher=root/'backup';password=root/'password'
            cipher.write_bytes(b'legacy'*30);password.write_bytes(b'fixture-password-only\n')
            with self.assertRaises(ValueError):backup.verify_archive(cipher,password)

if __name__=='__main__':unittest.main()
