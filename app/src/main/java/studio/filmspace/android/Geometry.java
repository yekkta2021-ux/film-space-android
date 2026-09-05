package studio.filmspace.android;
import java.nio.*;
import java.util.*;
final class Geometry {
    static class Mesh {FloatBuffer data;int count;Mesh(List<Float> list){count=list.size()/6;data=ByteBuffer.allocateDirect(list.size()*4).order(ByteOrder.nativeOrder()).asFloatBuffer();for(float f:list)data.put(f);data.position(0);}}
    static void vertex(List<Float> l,float x,float y,float z,float nx,float ny,float nz){l.add(x);l.add(y);l.add(z);l.add(nx);l.add(ny);l.add(nz);}
    static Mesh makeCube(){ArrayList<Float> a=new ArrayList<>();float[][] n={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};float[][][] p={{{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f},{.5f,-.5f,.5f}},{{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f},{-.5f,-.5f,-.5f}},{{-.5f,.5f,-.5f},{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f}},{{-.5f,-.5f,.5f},{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f}},{{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}},{{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}}};for(int f=0;f<6;f++)for(int k:new int[]{0,1,2,0,2,3})vertex(a,p[f][k][0],p[f][k][1],p[f][k][2],n[f][0],n[f][1],n[f][2]);return new Mesh(a);}
    static Mesh makeSphere(){ArrayList<Float> a=new ArrayList<>();for(int i=0;i<10;i++)for(int j=0;j<14;j++)for(int[] q:new int[][]{{i,j},{i+1,j},{i+1,j+1},{i,j},{i+1,j+1},{i,j+1}}){double t=Math.PI*q[0]/10,s=2*Math.PI*q[1]/14;float x=(float)(Math.sin(t)*Math.cos(s)),y=(float)Math.cos(t),z=(float)(Math.sin(t)*Math.sin(s));vertex(a,x,y,z,x,y,z);}return new Mesh(a);}
    static Mesh makeFloor(){ArrayList<Float> a=new ArrayList<>();for(int x=-20;x<20;x++)for(int z=-20;z<20;z++){float light=((x+z)&1)==0?1:.63f;for(int[] c:new int[][]{{0,0},{1,0},{1,1},{0,0},{1,1},{0,1}})vertex(a,x+c[0],-.003f,z+c[1],0,light,1-light);}return new Mesh(a);}

}
