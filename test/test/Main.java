package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import odml.core.Reader;
import odml.core.Section;
import odml.core.Writer;


public class Main {

    public static void main(String[] args) throws Exception {
        //new Tutorial().run();
        
        // it is really easy to write to file using streams ...
        Writer writer = new Writer(createTestTree());
        OutputStream oStream = new FileOutputStream(new File("test.odml"));
        writer.write(oStream);
        oStream.close();
        System.out.println("OdML written to file.");
        
        // ... and load again with streams
        Reader reader = new Reader();
        InputStream iStream = new FileInputStream(new File("test.odml"));
        Section section = reader.load(iStream);
        iStream.close();
        if (section != null)
            System.out.println("Loaded odML from file.");
    }
    
    
    private static Section createTestTree() throws Exception {
        Section root = new Section();
        Section person = new Section("Person");
        person.addProperty("firstName", "James");
        person.addProperty("age", 26);
        root.add(person);
        return root;
    }

}
