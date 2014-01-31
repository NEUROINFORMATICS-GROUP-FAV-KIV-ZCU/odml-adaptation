package test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import odml.core.Section;
import odml.core.Writer;


public class Main {

    public static void main(String[] args) throws Exception {
        //new Tutorial().run();
        
        // it is really easy to write to file using streams
        Writer writer = new Writer(createTestTree());
        OutputStream oStream = new FileOutputStream(new File("test.odml"));
        writer.write(oStream);
        oStream.close();
        System.out.println("written to file");
    }
    
    
    private static Section createTestTree() throws Exception {
        Section root = new Section();
        Section person = new Section("Person", "form");
        person.add(new Section("name", "item"));
        person.add(new Section("age", "item"));
        root.add(person);
        return root;
    }

}
