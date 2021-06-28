package fy.cfg.fore;

import java.io.*;
import java.util.Properties;

public class Config {

    public static void setProperties() {
        try (OutputStream os = new FileOutputStream("src/main/resources/graph.properties")) {
            Properties prop = new Properties();

            // set the properties
            prop.setProperty("node.cfg", "true");
            prop.setProperty("node.ast", "true");
            prop.setProperty("node.simplify", "true");
            prop.setProperty("edge.cfg", "true");
            prop.setProperty("edge.dataflow", "true");
            prop.setProperty("edge.ncs", "true");

            // save to disk
            prop.store(os, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Properties loadProperties() {
        try (InputStream is = new FileInputStream("src/main/resources/graph.properties")) {
            Properties prop = new Properties();
            prop.load(is);
            return prop;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        Config.setProperties();
    }
}
