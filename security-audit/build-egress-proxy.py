#!/usr/bin/env python3
"""Temporary loopback CONNECT relay for dependency downloads through a verified NIC.

No TLS interception, credentials, routing changes, daemon installation or arbitrary
destinations. Start only for a bounded build; terminate afterwards.
"""
import argparse
import select
import socket
import socketserver
import time
import threading

HOSTS={'pypi.org','files.pythonhosted.org','mirrors.tuna.tsinghua.edu.cn','pypi.tuna.tsinghua.edu.cn',
       'repo.maven.apache.org','repo1.maven.org'}

class Relay(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(10)
        first=self.rfile.readline(4097)
        if len(first)>4096:return
        parts=first.decode('ascii',errors='replace').strip().split()
        if len(parts)!=3 or parts[0]!='CONNECT':return
        host,separator,port=parts[1].rpartition(':')
        if not separator or host not in HOSTS or port!='443':return
        budget=16384
        while budget>0:
            line=self.rfile.readline(min(budget+1,4097));budget-=len(line)
            if line in (b'\r\n',b'\n'):break
            if not line:return
        if budget<=0:return
        upstream=None
        try:
            for address in socket.getaddrinfo(host,443,socket.AF_INET,socket.SOCK_STREAM):
                candidate=socket.socket(socket.AF_INET,socket.SOCK_STREAM)
                candidate.settimeout(10)
                candidate.setsockopt(socket.SOL_SOCKET,socket.SO_BINDTODEVICE,self.server.device.encode()+b'\0')
                try:candidate.connect(address[4]);upstream=candidate;break
                except OSError:candidate.close()
            if upstream is None:return
            self.wfile.write(b'HTTP/1.1 200 Connection Established\r\n\r\n');self.wfile.flush()
            deadline=time.monotonic()+180
            while time.monotonic()<deadline:
                readable,_,_=select.select([self.connection,upstream],[],[],15)
                if not readable:return
                for source in readable:
                    data=source.recv(65536)
                    if not data:return
                    (upstream if source is self.connection else self.connection).sendall(data)
        except (OSError,TimeoutError):pass
        finally:
            if upstream:upstream.close()

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True
    request_queue_size=8
    slots=threading.BoundedSemaphore(8)
    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request);return
        try:super().process_request(request,address)
        except BaseException:
            self.slots.release();raise
    def process_request_thread(self, request, address):
        try:super().process_request_thread(request,address)
        finally:self.slots.release()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--interface',choices=['enp1s0','enp2s0','wlp3s0'],required=True)
    args=parser.parse_args()
    with Server(('127.0.0.1',18889),Relay) as server:
        server.device=args.interface
        server.serve_forever()
