package test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import odml.core.OdmlWriter;
import odml.core.Section;


public class Main {

    public static void main(String[] args) throws Exception {
        //new Tutorial().run();
        
        OdmlWriter writer = new OdmlWriter(createTestTree());
        OutputStream oStream = new FileOutputStream(new File("pokus.odml"));
        writer.write(oStream);
        oStream.close();
        
        System.out.println("written to file");
    }
    
    
    private static Section createTestTree() throws Exception {
        Section root = new Section();
        Section person = new Section("Osoba", "form");
        person.add(new Section("jmeno", "item"));
        person.add(new Section("vek", "item"));
        root.add(person);
        return root;
    }

}
