package drz.tmdb.Memory.SystemTable;

import java.io.Serializable;

public class ClassTableItem implements Serializable {
    public String classname = "";        //类名
    public int classid = 0;                //类id
    public int attrnum = 0;                //类属性个数
    public int    attrid = 0;
    public String attrname = "";         //属性名
    public String attrtype = "";         //属性类型
    public String classtype = "";
    public String alias="";

    public ClassTableItem(String classname, int classid, int attrnum,int attrid, String attrname, String attrtype,String classtype,String alias) {
        this.classname = classname;
        this.classid = classid;
        this.attrnum = attrnum;
        this.attrname = attrname;
        this.attrtype = attrtype;
        this.attrid = attrid;
        this.classtype = classtype;
        this.alias = alias;
    }
    public ClassTableItem(){}

    public ClassTableItem getCopy(){
        return new ClassTableItem(this.classname,this.classid,this.attrnum,this.attrid,this.attrname,this.attrtype,this.classtype,this.alias);
    }


}
