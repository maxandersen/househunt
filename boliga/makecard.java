///usr/bin/env jbang "$0" "$@" ; exit $?
//REPOS mavencentral,jitpack
//DEPS io.quarkus:quarkus-bom:${quarkus.version:2.2.0.CR1}@pom
//DEPS io.quarkus:quarkus-qute
//DEPS net.sf.supercsv:super-csv:2.4.0

//JAVA 16+

import io.quarkus.qute.Engine;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;

import javax.inject.Inject;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@QuarkusMain
public class makecard implements QuarkusApplication {

    @Inject
    Engine qute;

   // @RestClient
   // PlaylistService playlistService;

    public int run(String... args) throws Exception {

        List<Map<String,String>> houses = new ArrayList<>();

        try(ICsvMapReader mapReader = new CsvMapReader(new FileReader("houses.csv"), CsvPreference.STANDARD_PREFERENCE)) {
                
                // the header columns are used as the keys to the Map
                final String[] header = mapReader.getHeader(true);
               // final CellProcessor[] processors = getProcessors();
                
                Map<String, String> row;
                while( (row = mapReader.read(header)) != null ) {
                    houses.add(row);
                }
        }

        Files.writeString(Path.of("housecards.adoc"), qute.parse(Files.readString(Path.of("housecard.adoc.qute")))
                .data("houses", houses)
                .render());

        return 0;
    }

    }

