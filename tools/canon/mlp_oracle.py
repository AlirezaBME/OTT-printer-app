#!/usr/bin/env python3
"""Invoke Canon's actual USB MLP serializers with fake I/O; never opens USB.
The library ABI offsets below apply only to the checksum-pinned v5.00 x86_64
reference binary. No proprietary implementation or binary ships in the APK.
"""
import ctypes as C
import json
import os
from pathlib import Path
import subprocess
import sys
from reference_driver import SHA256

root = Path(sys.argv[1]).resolve()
libroot = root / 'binary/usr/lib'
bidi = libroot / 'Canon/CUPS_SFPR/Bidi'
here = Path(__file__).resolve().parent
# Preserve Info_Initialize_FilterCalled: the earlier CPCA recorder replaced it
# and therefore never discovered multi_usb_ncap or the USB MLP plugin.
subprocess.run(['gcc', '-shared', '-fPIC', '-DCANON_RECORD_REAL_INITIALIZATION', '-o', str(root/'session_realinit.so'), str(here/'session_recorder.c'), '-ldl'], check=True)
subprocess.run(['gcc', '-shared', '-fPIC', '-o', str(root/'comm_selection_recorder.so'), str(here/'comm_selection_recorder.c')], check=True)
ppd = (root/'lbp6030-reference.ppd').read_text().replace(str(libroot/'Canon/CUPS_SFPR/Libs/libCUPS_Communicatorr.so'), str(root/'comm_selection_recorder.so'))
(root/'mlp-selection.ppd').write_text(ppd)
env = os.environ | {'PRINTER':'reference', 'CANON_CAPTURE_PPD':str(root/'mlp-selection.ppd'), 'CANON_REFERENCE_ROOT':str(root), 'CANON_CPCA_CAPTURE':str(root/'mlp-inner-job.prn'), 'LD_PRELOAD':str(root/'session_realinit.so'), 'PPD':str(root/'mlp-selection.ppd'), 'DEVICE_URI':'usb://Canon/LBP6030?serial=reference'}
with (root/'mlp-selection.log').open('wb') as log, (root/'mlp-selection-out.prn').open('wb') as out:
    subprocess.run([str(libroot/'cups/filter/rastertosfp'), '1','reference','Canon transport test','1','PageSize=A4 Resolution=600',str(root/'reference.raster')], env=env, stderr=log, stdout=out, timeout=30, check=True)
selection = (root/'mlp-selection.log').read_text()
assert 'uri=multi_usb_ncap://' in selection, selection
cups=C.CDLL(str(libroot/'Canon/CUPS_SFPR/Libs/libCUPS_Communicatorr.so.1.0.0'))
cups.getSchemeType.argtypes=[C.c_char_p];cups.getSchemeType.restype=C.c_int
cups.selectJobPluginAuto.argtypes=[C.c_char_p,C.c_int];cups.selectJobPluginAuto.restype=C.c_int
assert cups.getSchemeType(b'multi_usb_ncap://Canon/LBP6030')==3
assert cups.selectJobPluginAuto(b'multi_usb_ncap://Canon/LBP6030',2)==3
cups.makePathOfJobPlugin.argtypes=[C.c_int,C.c_char_p];cups.makePathOfJobPlugin.restype=C.c_char_p
plugin=cups.makePathOfJobPlugin(3,str(libroot/'Canon/CUPS_SFPR').encode()).decode()
assert 'libcomm_usbmlportr.so' in plugin,plugin
library=C.CDLL(str(bidi/'libcomm_usbmlportr.so.1.0.0'),mode=C.RTLD_LOCAL)
port=C.addressof((C.c_char*2048).in_dll(library,'g_usbport'))
original=C.c_void_p.from_address(port).value
fake=(C.c_void_p*16)(*(C.c_void_p*16).from_address(original))
wire=[];requests=[]
W=C.CFUNCTYPE(C.c_long,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t))
R=C.CFUNCTYPE(C.c_long,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t),C.c_ulong)
Q=C.CFUNCTYPE(C.c_long,C.c_void_p,C.c_int,C.c_void_p,C.c_size_t,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t),C.c_ulong)
@W
def write(p,b,n,out):wire.append(C.string_at(b,n));out[0]=n;return 0
@R
def read(p,b,n,out,t):
    reply=bytes.fromhex('000000090100800008');assert n==9
    C.memmove(b,reply,9);out[0]=9;return 0
@Q
def query(p,c,b,n,out,cap,actual,t):
    data=C.string_at(b,n);requests.append(data)
    reply=bytes([0x81,0,data[1],data[2]])+bytes.fromhex('40004000ffff0001') if data[0]==1 else bytes([0x82,0,data[1],data[2]])
    C.memmove(out,reply,len(reply));actual[0]=len(reply);return 0
fake[2]=C.cast(write,C.c_void_p).value;fake[3]=C.cast(read,C.c_void_p).value;fake[8]=C.cast(query,C.c_void_p).value
C.c_void_p.from_address(port).value=C.addressof(fake)
def call(name,p):
    method=getattr(library,name);method.argtypes=[C.c_void_p];method.restype=C.c_long;return method(p)
passed=[]
def serialize(host,payload,maximum=65535,flag=0):
    channel=port+0x80+host*0xc8
    C.c_ulong.from_address(channel+0x38).value=maximum
    C.c_ulong.from_address(channel+0x48).value=1
    buffer=C.create_string_buffer(payload);send=(C.c_ulong*5)(C.addressof(buffer),len(payload),0,0,flag)
    C.c_void_p.from_address(channel+0x58).value=C.addressof(send)
    at=len(wire)
    try:
        assert call('_ZN12C_MLCChannel8SendSub2Ev',channel)==0
        assert C.c_ulong.from_address(channel+0x48).value==0
        assert len(wire[at:])==2 and len(wire[at])==6, 'Native header/payload transfer boundaries changed'
        packet=b''.join(wire[at:])
        return packet,send[2]
    finally:C.c_void_p.from_address(channel+0x58).value=None
try:
    assert call('_ZN9C_USBPort7InitSubEv',port)==0
    assert wire==[bytes.fromhex('0000000801000008')]
    passed.append({'name':'initialize','wireHex':wire[0].hex()})
    C.c_int.from_address(port+0x28).value=1
    for host in range(1,4):
        channel=port+0x80+host*0xc8
        assert C.string_at(channel+0x30,2)==bytes([host,host*16])
        assert call('_ZN12C_MLCChannel7OpenSubEv',channel)==0
        assert C.c_ulong.from_address(channel+0x38).value==16384
        assert C.c_ulong.from_address(channel+0x40).value==16384
        assert C.c_ulong.from_address(channel+0x48).value==1
        assert requests[-1]==bytes([1,host,host*16])+b'\xff'*6
        packet,consumed=serialize(0,requests[-1]);assert consumed==9
        assert packet==bytes.fromhex('0000000f0100')+requests[-1]
        passed.append({'name':f'open-{host}','wireHex':packet.hex()})
        assert call('_ZN12C_MLCChannel8CloseSubEv',channel)==0
        packet,consumed=serialize(0,requests[-1]);assert consumed==3
        assert packet==bytes.fromhex('000000090100')+bytes([2,host,host*16])
        passed.append({'name':f'close-{host}','wireHex':packet.hex()})
    for count in [1,8,64,506,16378,65529]:
        payload=bytes((i*31)&255 for i in range(count))
        packet,consumed=serialize(1,payload)
        assert consumed==count
        assert packet==bytes([1,16,(count+6)>>8,(count+6)&255,1,0])+payload
        passed.append({'name':f'data-{count}','headerHex':packet[:6].hex(),'payloadBytes':consumed})
    # Actual RecvSub restores one credit for an empty acknowledgement, even
    # when the header credit byte is zero. Do not infer DOT4 credit semantics.
    for credit_byte in [0,1,255]:
        channel=port+0x148
        C.c_int.from_address(channel).value=1
        C.c_ulong.from_address(channel+0x48).value=0
        header=C.create_string_buffer(bytes([1,16,0,6,credit_byte,0]))
        method=library._ZN12C_MLCChannel7RecvSubEPK11CMLP_HEADER
        method.argtypes=[C.c_void_p,C.c_void_p];method.restype=C.c_long
        assert method(channel,header)==0
        assert C.c_ulong.from_address(channel+0x48).value==1
        passed.append({'name':f'credit-{credit_byte}','restored':1})
finally:
    C.c_void_p.from_address(port).value=original
result={'oracle':'Canon v5.00 libcomm_usbmlportr.so','driverArchiveSha256':SHA256,'actualInfoInitializationRetained':True,'selectedDeviceUri':'multi_usb_ncap://Canon/LBP6030?serial=reference','selectedPlugin':Path(plugin).name,'vectors':passed,'physicalPrintVerified':False}
(root/'mlp-oracle-results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
