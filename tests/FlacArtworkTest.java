package com.victor.flacprueba;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class FlacArtworkTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static byte[] picture(int type,String mime,byte[] bytes)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);d.writeInt(type);byte[] m=mime.getBytes(StandardCharsets.UTF_8);d.writeInt(m.length);d.write(m);d.writeInt(0);for(int i=0;i<4;i++)d.writeInt(1);d.writeInt(bytes.length);d.write(bytes);return out.toByteArray();}
    static byte[] flac(byte[][] blocks,int[] types)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();o.write(new byte[]{'f','L','a','C'});for(int i=0;i<blocks.length;i++){byte[] b=blocks[i];o.write(types[i]|(i==blocks.length-1?128:0));o.write(b.length>>16);o.write(b.length>>8);o.write(b.length);o.write(b);}return o.toByteArray();}
    static byte[] read(byte[] b)throws Exception{return FlacArtwork.read(new ByteArrayInputStream(b));}
    static void le(DataOutputStream d,int n)throws Exception{d.writeInt(Integer.reverseBytes(n));}
    public static void main(String[] args)throws Exception{
        byte[] front={1,2,3},back={4,5},pic=picture(3,"image/jpeg",front);
        check(Arrays.equals(read(flac(new byte[][]{new byte[34],pic},new int[]{0,6})),front));
        check(Arrays.equals(read(flac(new byte[][]{picture(4,"image/png",back),pic},new int[]{6,6})),front));
        check(Arrays.equals(read(flac(new byte[][]{pic,picture(4,"image/png",back)},new int[]{6,6})),front));
        check(read(flac(new byte[][]{new byte[34]},new int[]{0}))==null);
        check(read(flac(new byte[][]{picture(3,"-->","https://example.test".getBytes())},new int[]{6}))==null);
        check(read(flac(new byte[][]{new byte[]{0,0,0,3,127,127,127,127}},new int[]{6}))==null);
        ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);le(d,0);le(d,1);byte[] field=("METADATA_BLOCK_PICTURE="+Base64.getEncoder().encodeToString(pic)).getBytes(StandardCharsets.UTF_8);le(d,field.length);d.write(field);
        check(Arrays.equals(read(flac(new byte[][]{out.toByteArray()},new int[]{4})),front));
        byte[] f=flac(new byte[][]{pic},new int[]{6});out.reset();out.write(new byte[]{'I','D','3',3,0,0,0,0,0,0});out.write(f);check(Arrays.equals(read(out.toByteArray()),front));
        boolean truncated=false;try{read(Arrays.copyOf(f,f.length-1));}catch(EOFException e){truncated=true;}check(truncated);
        check(read(new byte[]{'O','g','g','S'})==null);
        System.out.println("10 pruebas de portada FLAC superadas.");
    }
}
