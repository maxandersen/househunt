//DEPS com.microsoft.playwright:playwright:1.14.0
//DEPS com.github.tony19:named-regexp:0.2.5
//DEPS net.sf.supercsv:super-csv:2.4.0
//DEPS info.picocli:picocli:4.2.0

import com.microsoft.playwright.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.code.regexp.*;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;
import picocli.CommandLine;

import static java.lang.System.out;

public class housecard implements Callable<Integer> {
  public static void main(String[] args) throws IOException {
     int exitCode = new CommandLine(new housecard()).execute(args);
      System.exit(exitCode);
    }

    @CommandLine.Parameters(split = "\n")
    List<String> adresses;

    @CommandLine.Option(names="--headless")
    boolean headless;

  @CommandLine.Option(names="--debug", defaultValue = "false")
  boolean debug;

  static boolean first = true;

  @Override
  public Integer call() throws Exception {


    List<Map<String, String>> properties = new ArrayList<>();
    Set<String> headers = new HashSet<>();

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
              .setHeadless(headless));
      BrowserContext context = browser.newContext();

      // Open new page
      Page page = context.newPage();

      for (String adr : adresses) {
        System.out.println("Looking up: " + adr);
        page = findByAddress(adr, page);

        Map<String, String> property = extractPropertyInfo(page);
        properties.add(property);
        headers.addAll(property.keySet());
      }

    }


      String[] header = headers.toArray(new String[0]);

      try(ICsvMapWriter mapWriter =new CsvMapWriter(new FileWriter("houses.csv"), CsvPreference.STANDARD_PREFERENCE)) {

        // write the header
        mapWriter.writeHeader(header);

        properties.forEach(row -> {
          try {
            mapWriter.write(row, header);
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
      }

      return 0;
  }

  private Map<String, String> extractPropertyInfo(Page page) {
    Map<String,String> property = new HashMap<>();

    property.put("imageUrl", (page.querySelector("picture").querySelector("img").getAttribute("src")));

    var priceinfo = page.querySelector("app-bvs-property-price").textContent();

    if(debug) out.println("price info:\n" + priceinfo);

    var propertyDetails = page.textContent("app-bvs-property-details.h-100");

    if(debug) out.println("property details:\n" + propertyDetails);

    var pricematcher = Pattern.compile(".*BBR- og boliginformationer.+?%?\\s+(?<price>[0-9\\.]+).*?kr.\\s+Udbetaling\\s+(?<payout>[0-9\\.]+).*?kr.\\s+(?<pricepersqm>[0-9\\.]+)");

    var matcher = pricematcher.matcher(priceinfo);

    property.put("address", page.querySelector(".house-place").textContent().trim());

    property.put("agencyUrl", page.querySelector("[data-gtm=see_property_at_agency_btn]").getAttribute("href"));

    property.put("boligaUlr", page.url());

    if(matcher.find()) {
      property.putAll(matcher.namedGroups().get(0));
    }

    // System.out.println(propertyDetails);

    var detailspattern = Pattern.compile(".*?Oprettet (?<createdDate>.*?)  *Tid.*Tid på markedet: (?<timeOnMarket>.*?) Boligstørrelse.*Boligstørrelse: (?<houseSize>[0-9]+) m.\\s+Værelser.*?Værelser: (?<rooms>[0-9]+).*Ejerudgift.+?Ejerudgift:\\s(?<ownerExpense>[0-9\\.]+).*Bygge.+?Byggeår: (?<yearBuilt>[0-9]+).+?Grundstørrelse.+?Grundstørrelse: (?<lotSize>[0-9\\.]+).*Energimærke.+Energimærke: (?<energy>\\w+)");

    matcher = detailspattern.matcher(propertyDetails);

    if(matcher.find()) {
      property.putAll(matcher.namedGroups().get(0));
    }
    return property;
  }

  private static Page findByAddress(String adr, Page page) {
    // Go to https://www.boliga.dk/
    page.navigate("https://www.boliga.dk/");

    if(first) {
      // Click [aria-label="Accepter alle"]
      page.click("[aria-label=\"Accepter alle\"]");
      first = false;
    }
    // Click [placeholder="Indtast vej, by, postnr., kommune eller landsdel"]
    page.click("[placeholder=\"Indtast vej, by, postnr., kommune eller landsdel\"]");

    // Fill [placeholder="Indtast vej, by, postnr., kommune eller landsdel"]
    page.fill("[placeholder=\"Indtast vej, by, postnr., kommune eller landsdel\"]", adr);

    // Click button:has-text("Langtvedvej 19, 5540 Ullerslev")
    // page.waitForNavigation(new Page.WaitForNavigationOptions().setUrl("https://www.boliga.dk/bolig/1710564/langtvedvej_19_5540_ullerslev"), () ->
    page.waitForNavigation(() -> {
      page.click(".item > span:nth-child(1)");
    });

    page.click("text=Vis mere");

    return page;
  }
}