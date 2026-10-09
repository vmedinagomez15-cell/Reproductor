package com.victor.flacprueba;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Bounded local FLAC metadata reader. Never follows linked picture URLs. */
public final class FlacArtwork {
    private static final int MAX_BLOCK=16*1024*1024, MAX_METADATA=64*1024*1024;
    private static final class Picture { final byte[] data; final int rank; Picture(byte[] d,int r){data=d;rank=r;} }
    public static byte[] read(InputStream source)throws IOException {
        if(source==null)return null;
        DataInputStream in=new DataInputStream(new BufferedInputStream(source));
        byte[] magic=new byte[4];in.readFully(magic);
        if(magic[0]=='I'&&magic[1]=='D'&&magic[2]=='3'){
            int version=magic[3]&255;in.readUnsignedByte();int flags=in.readUnsignedByte();int length=0;
            for(int i=0;i<4;i++){int n=in.readUnsignedByte();if(n>127)throw new IOException("Invalid ID3 length");length=(length<<7)|n;}
            if(length>MAX_BLOCK)throw new IOException("ID3 too large");
            skip(in,length+((version==4&&(flags&16)!=0)?10:0));in.readFully(magic);
        }
        if(magic[0]!='f'||magic[1]!='L'||magic[2]!='a'||magic[3]!='C')return null;
        long total=0;Picture best=null;
        for(int blocks=0;blocks<10000;blocks++){
            int header=in.readUnsignedByte();int type=header&127;
            int len=(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();
            total+=len+4L;if(total>MAX_METADATA)throw new IOException("Metadata too large");
            if(type==6||type==4){byte[] block=new byte[len];in.readFully(block);Picture p=type==6?picture(block):comments(block);if(p!=null&&(best==null||p.rank>best.rank))best=p;}
            else skip(in,len);
            if((header&128)!=0)return best==null?null:best.data;
        }
        throw new IOException("Too many metadata blocks");
    }
    private static void skip(InputStream in,long n)throws IOException{while(n>0){long s=in.skip(n);if(s==0){if(in.read()==-1)throw new EOFException();s=1;}n-=s;}}
    private static long uint(DataInputStream in)throws IOException{return ((long)in.readInt())&0xffffffffL;}
    private static byte[] field(DataInputStream in)throws IOException{long n=uint(in);if(n>MAX_BLOCK||n>in.available())throw new IOException("Invalid picture field");byte[] bytes=new byte[(int)n];in.readFully(bytes);return bytes;}
    private static Picture picture(byte[] block){try{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(block));long type=uint(in);String mime=new String(field(in),StandardCharsets.US_ASCII);field(in);skip(in,16);byte[] data=field(in);
        if("-->".equals(mime)||data.length==0)return null;
        return new Picture(data,type==3?100:(type==1||type==2?1:10));
    }catch(IOException|RuntimeException e){return null;}}
    private static int little(DataInputStream in)throws IOException{long n=Integer.reverseBytes(in.readInt())&0xffffffffL;if(n>MAX_BLOCK)throw new IOException("Invalid comment length");return (int)n;}
    private static Picture comments(byte[] block){try{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(block));int vendor=little(in);if(vendor>in.available())return null;skip(in,vendor);int count=little(in);if(count>in.available()/4)return null;Picture best=null;
        for(int i=0;i<count;i++){int n=little(in);if(n>in.available())return best;byte[] b=new byte[n];in.readFully(b);String value=new String(b,StandardCharsets.UTF_8);int eq=value.indexOf('=');if(eq<0)continue;String name=value.substring(0,eq);Picture p=null;
            try{if(name.equalsIgnoreCase("METADATA_BLOCK_PICTURE"))p=picture(Base64.getDecoder().decode(value.substring(eq+1)));else if(name.equalsIgnoreCase("COVERART"))p=new Picture(Base64.getDecoder().decode(value.substring(eq+1)),5);}catch(IllegalArgumentException ignored){}
            if(p!=null&&(best==null||p.rank>best.rank))best=p;
        }return best;
    }catch(IOException|RuntimeException e){return null;}}
}
