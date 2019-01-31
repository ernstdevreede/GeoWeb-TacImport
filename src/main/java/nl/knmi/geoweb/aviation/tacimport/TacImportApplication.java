package nl.knmi.geoweb.aviation.tacimport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import nl.knmi.geoweb.aviation.tacimport.taf.TacImportConfiguration;
import nl.knmi.geoweb.aviation.tacimport.taf.TafImporter;

@SpringBootApplication
@ContextConfiguration(classes = TacImportConfiguration.class, loader= AnnotationConfigContextLoader.class)
public class TacImportApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(TacImportApplication.class, args);
    }

    @Autowired
    private TafImporter tafImporter;

    @Override
    public void run(final String... args) throws Exception {
            tafImporter.importTAFs("/nobackup/users/vreedede/SESAR/BREM_archive", "tafs_tac_201[678].txt",
                    "/nobackup/users/vreedede/SESAR/archivedTAFS", "FTNL31", "EHDB" );
/*        for (String s: args) {
            System.err.println(s);
            tafImporter.importFile(s);
        }*/
    }
}
