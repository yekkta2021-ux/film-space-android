package studio.filmspace.android;
public final class GeometryCheck {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        Geometry.Mesh box=Geometry.makeCube(),sphere=Geometry.makeSphere(),floor=Geometry.makeFloor();
        require(box.count==36,"Cube must have 12 triangles");
        require(floor.count==40*40*6,"Floor must cover the entire 40m grid");
        for(Geometry.Mesh mesh:new Geometry.Mesh[]{box,sphere,floor}){
            require(mesh.count%3==0,"Complete triangles required");
            for(int i=0;i<mesh.data.limit();i++)require(Float.isFinite(mesh.data.get(i)),"All mesh components must be finite");
        }
        for(int i=0;i<sphere.count;i++){
            float x=sphere.data.get(i*6),y=sphere.data.get(i*6+1),z=sphere.data.get(i*6+2);
            require(Math.abs(x*x+y*y+z*z-1)<.0001,"Stand-in surfaces must stay on the unit sphere");
        }
        for(int i=0;i<box.count;i++)for(int k=0;k<3;k++)require(Math.abs(box.data.get(i*6+k))==.5f,"Box bounds must be centered");
        System.out.println("PASS: mesh integrity, sphere surface, box bounds, complete floor coverage");
    }
}
