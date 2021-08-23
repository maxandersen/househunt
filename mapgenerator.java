///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
// //DEPS com.google.maps:google-maps-services:0.19.0
// //DEPS org.slf4j:slf4j-simple:1.7.25

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.lang.Math.*;
import static java.lang.Math.log;
import static java.lang.String.valueOf;
import static java.lang.System.out;
import static picocli.CommandLine.*;

@Command(name = "mapgen", mixinStandardHelpOptions = true, version = "mapgen 0.1",
        description = "mapgen made with jbang")
class housedistance implements Callable<Integer> {

    final long MAXSIZE = 640;

    //ported/inspired by https://stackoverflow.com/questions/7490491/capture-embedded-google-map-image-with-python-without-using-a-browser

    //circumference/radius
    final double tau = 6.283185307179586;
    // One degree in radians, i.e. in the units the machine uses to store angle,
    // which is always radians. For converting to and from degrees. See code for
    // usage demonstration.
    final double DEGREE = tau/360;

    final long ZOOM_OFFSET = 8;
    final long LOGO_CUTOFF = 32;

    @Spec CommandSpec spec;

    static enum MapType {
        roadmap, satellite, terrain, hybrid;
    }

    @Option(names="--maptype", defaultValue = "roadmap", description = "Valid values: ${COMPLETION-CANDIDATES}")
    MapType maptype;

    @Option(names="--scale", defaultValue = "2", description = "Valid values: 1 (normal) or 2 (high density)")
    int scale;

    @Option(names="--zoom", defaultValue = "10", description = "Zoom from 1 to 21+")
    int zoom;

    Coordinate nw;
    Coordinate se;

    @Option(names="--coordinates", description = "Pair of Latitude and longitude for the rectangle to render. Specficied as 4 comma separated values",split=",")
    public void setcoordiante(Double[] val) {
        if(val ==null || val.length!=4) {
            throw new ParameterException(spec.commandLine(),
                    String.format("Coordinate must be exactly 4 values for '--coordinates'"));
        }
        nw = new Coordinate(val[0]*DEGREE,val[1]*DEGREE);
        se = new Coordinate(val[2]*DEGREE,val[3]*DEGREE);
    }

    @Option(names = "--api-key", required = true)
    String GOOGLE_MAPS_API_KEY;

    @Option(names = "--out", defaultValue = "result.png", description = "Name of png image filename")
    File output;

    static record Coordinate(double px, double py) {
        @Override
        public String toString() {
            return px + "," + py;
        }
    };

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    Coordinate latlon2pixels(double lat, double lon, long zoom) {
        var mx = lon;
        var my = log(tan((lat + tau / 4) / 2));
        var res = Math.pow( 2,(zoom + ZOOM_OFFSET)) / tau;
        var px = mx * res;
        var py = my * res;
        return new Coordinate(px,py);
    }

    Coordinate pixels2latlon(double px, double py, long zoom) {
        var res = pow(2,(zoom + ZOOM_OFFSET))/tau;
        var mx = px / res;
        var my = py / res;
        var lon = mx;
        var lat = 2 * atan(exp(my)) - tau / 4;
        return new Coordinate(lat,lon);
    }

    BufferedImage get_maps_image(Coordinate NW_lat_long, Coordinate SE_lat_long, long zoom) throws IOException {

        Coordinate ul = NW_lat_long;
        Coordinate lr = SE_lat_long;

        // convert all these coordinates to pixels
        Coordinate ulpx = latlon2pixels(ul.px, ul.py, zoom);
        var ulx = ulpx.px;
        var uly = ulpx.py;

        Coordinate lrpx = latlon2pixels(lr.px, lr.py, zoom);
        var lrx = lrpx.px;
        var lry = lrpx.py;

        // calculate total pixel dimensions of final image
        //dx, dy = lrx - ulx, uly - lry
        var dx = lrx - ulx;
        var dy = uly - lry;

        // calculate rows and columns
        var cols = ceil(dx / MAXSIZE);
        var rows = ceil(dy / MAXSIZE);

        //calculate pixel dimensions of each small image
        var width = ceil(dx / cols);
        var height = ceil(dy / rows);

        var heightplus = height + LOGO_CUTOFF*scale;

        // assemble the image from stitched
        BufferedImage image = new BufferedImage((int) dx*scale, (int) dy*scale, BufferedImage.TYPE_INT_ARGB);

        var finalImage = image.createGraphics();

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {

                var dxn = width * (0.5 + x);
                var dyn = height * (0.5 + y);
                var latnlonn = pixels2latlon(
                        ulpx.px + dxn, ulpx.py - dyn - (LOGO_CUTOFF*scale) / 2, zoom);
                String position = String.join(",", valueOf(latnlonn.px / DEGREE), valueOf(latnlonn.py / DEGREE));
                out.println(String.join(" ", valueOf(x), valueOf(y), position));
                Map<String, ? extends Serializable> urlparams = Map.of("center", position,
                        "zoom", valueOf(zoom),
                        "size", String.format("%sx%s", (int)width, (int)heightplus),
                        "maptype", maptype,
                        "sensor", "false",
                        "scale", scale,
                        "key", GOOGLE_MAPS_API_KEY);

                String params = urlparams.entrySet().stream()
                        .map(p -> encode(p.getKey()) + "=" + encode(p.getValue().toString()))
                        .reduce((p1, p2) -> p1 + "&" + p2)
                        .orElse("");

                String url = "http://maps.google.com/maps/api/staticmap?" + params;

                // draws cropped (sub) image to avoid logo overlap
                BufferedImage subimage = ImageIO.read(new URL(url)).getSubimage(0, 0, (int) width*scale, (int) height*scale);

                finalImage.drawImage(subimage,
                                  (int)(x*width)*scale, (int)(y*height)*scale, (int)width*scale, (int)height*scale, null);

               // ImageIO.write(subimage,"png", new File("result-" + x + "x" + y + ".png"));
                }
            }

            return image;
        }

    public static void main(String... args) {
        int exitCode = new CommandLine(new housedistance()).execute(args);


        System.exit(exitCode);
    }


    @Override
    public Integer call() throws Exception { // your business logic goes here...

       // 55.666351096050406, 9.720840430580278
        //55.02199390467383, 11.03809648010498
             var image = get_maps_image(nw,se,
                    zoom);

            ImageIO.write(image,"png",output);

        JOptionPane.showMessageDialog(null, null, "Image", JOptionPane.INFORMATION_MESSAGE,
                new ImageIcon(image));
        return 0;
    }
}
