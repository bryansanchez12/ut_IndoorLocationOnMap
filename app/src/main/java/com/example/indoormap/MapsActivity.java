package com.example.indoormap;


import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BuildConfig;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, BeaconConsumer, BootstrapNotifier, RangeNotifier {

    private GoogleMap mMap;
    private Button search;
    TextView textView;
    TextView FLOOR;
    String TAG ="main";
    Map<String, Double> strongBeacons = new HashMap<>();
    Map< String , ArrayList> beacons  = new HashMap<>();
      // contains three beacons with the strongest signal
    List<String> values = new ArrayList<>();
    double[] ownLocation = new double[2]; // the coordinates of our location
    private boolean first_marker_placed;
    private Marker markerPos;
    ProgressDialog dialog;
    int scannedBeaconCounter = 0;
    private static final double SPEED_OF_LIGHT = 299792458;

    //----- Beacons Part------//
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_BACKGROUND_LOCATION = 2;

    public static final String M_TAG    = "Monitoring_iBeacons";
    protected static final String R_TAG = "RangingBeacons";

    private BeaconManager beaconManager;
    private RegionBootstrap regionBootstrap;
    BackgroundPowerSaver backgroundPowerSaver;

    //------- GLOBAL COUNTING FOR BEACONS ----------//
    private int  nMeasures = 0;
    private ArrayList<double[]> locations = new ArrayList<>();

    public MapsActivity() {
    }


    /**
     * Creates the activity "Indoor Mapping" and initializes all object inside
     * @param savedInstanceState
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readBeaconData();
        //Log.d("beacons_list",beacons.get("f7:9a:0a:c7:81:f3").toString());
        setContentView(R.layout.activity_maps);
        FLOOR = findViewById(R.id.FLOOR);

        //Obtain the SupportMapFragment and get notified when the map is ready to be used.
        initializeLocationManager();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        init();
        textView = (TextView) findViewById(R.id.textView);

        search = findViewById(R.id.searchBeacons);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    //onDestroy();
                    startBeacon();
                } catch (Exception e){
                    Log.i("ERROR", e.getMessage());
                }
            }
        });

    }



    // -------------------------------------------------------------------------------------
    // -----------------------         Beacon Manager Part        --------------------------
    // -------------------------------------------------------------------------------------



    /**
     * Initialize the Beacon features and set a time period when it should scan for new beacons
     */
    public void init (){
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(2000);
        beaconManager.setBackgroundScanPeriod(2000);
    }


    /**
     * Start looking for iBeacons near to selected region
     */
    @SuppressLint("WrongConstant")
    public void startBeacon(){
        Region region = new Region("backgroundRegion", null, null, null);
        regionBootstrap = new RegionBootstrap(this, region);
        backgroundPowerSaver = new BackgroundPowerSaver(this);

        // Show the progress bar and its text view when it is scanning
        dialog = ProgressDialog.show(this, "Scanning",
                "Loading. Please wait...", true);


        beaconManager.bind(this);
        beaconManager.setEnableScheduledScanJobs(false);
    }


    /**
     * Finish the current scanning phase by unbinding the beacon manager
     */
    public void endScanning(){
        try {
            onDestroy();
        } catch (Exception e){
            Log.i("ERROR", e.getMessage());
        }
    }



    // -------------------------------------------------------------------------------------
    // -----------------------         Google Maps methods        --------------------------
    // -------------------------------------------------------------------------------------



    /**
     * Request permission from the device by showing a pop-up
     */
    private void initializeLocationManager() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_DENIED) {

                Log.d("permission", "permission denied to get location - requesting it");
                String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
                requestPermissions(permissions, PERMISSION_REQUEST_FINE_LOCATION );
            }
        }

    }


    /**
     * Takes the latitude and longitude and places a marker of the current location
     * @param lat latitude on the map
     * @param lng longitude on the map
     */
    public void drawPosition (double lat, double lng){
        LatLng position = new LatLng(lat, lng);
        if (first_marker_placed == false) {
            markerPos = mMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title("My Current Location"));
            first_marker_placed = true;
        }else {
            markerPos.setPosition(position);
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add a marker in Sydney and move the camera
        LatLng ravelijn = new LatLng(52.23934713975484, 6.855547836657308);
        mMap.moveCamera( CameraUpdateFactory.newLatLngZoom(ravelijn, 19.0f) );
    }



    // -------------------------------------------------------------------------------------
    // -------------------         Reading iBeacons position        ------------------------
    // -------------------------------------------------------------------------------------



    /**
     * From the file "beacons-Ravelijn.xls", it reads all rows and add every element on a list
     * that later on, such a list will be added to the hashmap beacons
     */
    public void readBeaconData(){

        try {
            InputStream myInput;
            // initialize asset manager
            AssetManager assetManager = getAssets();
            //  open excel sheet
            myInput = assetManager.open("beacons-Ravelijn.xls");
            // Create a POI File System object
            POIFSFileSystem myFileSystem = new POIFSFileSystem(myInput);
            // Create a workbook using the File System
            HSSFWorkbook myWorkBook = new HSSFWorkbook(myFileSystem);
            // Get the first sheet from workbook
            HSSFSheet mySheet = myWorkBook.getSheetAt(0);
            // We now need something to iterate through the cells.

            // We now need something to iterate through the cells.
            Iterator<Row> rowIter = mySheet.rowIterator();
            // check if there is more row available
            int rowno =0;
            // check if there is more column available
            while (rowIter.hasNext()) {
                ArrayList lst = new ArrayList();
                Log.e(TAG, " row no "+ rowno );
                HSSFRow myRow = (HSSFRow) rowIter.next();
                if(rowno !=0) {
                    Iterator<Cell> cellIter = myRow.cellIterator();
                    int colno =0;
                    String id="", name="", mac="", lng="", lat="", floor="";
                    while (cellIter.hasNext()) {
                        HSSFCell myCell = (HSSFCell) cellIter.next();
                        if (colno==0){
                            id = myCell.toString();
                        }else if (colno==1){
                            name = myCell.toString();
                        }else if (colno==2){
                            mac = myCell.toString();
                        }else if (colno==3){
                            lng = myCell.toString();
                        }else if (colno==4){
                            lat = myCell.toString();
                        }else if (colno==5){
                            floor = myCell.toString();
                        }
                        colno++;
                        Log.e(TAG, " Index :" + myCell.getColumnIndex() + " -- " + myCell.toString());
                    }
                    lst.add(id);
                    lst.add(name);
                    lst.add(lat);
                    lst.add(lng);
                    lst.add(floor);
                    beacons.put(mac, lst);
                }
                rowno++;
            }

        } catch (Exception e) {
            Log.e(TAG, "error "+ e.toString());
        }
    }


    // -------------------------------------------------------------------------------------
    // --------------------------         Triangulation         ----------------------------
    // -------------------------------------------------------------------------------------


    /**
     * Localizing our device within a certain range.
     *
     * The closest two points from the intersection set are selected.
     * Their average location is an approximation of our device's location.
     */
    public void intersection(String mac1, String mac2, String mac3){ // all beacons on the same floor
        List<double[]> points = new ArrayList<double[]>();
        twoCircleIntersection(mac1, mac2, points);
        twoCircleIntersection(mac1, mac3, points);
        twoCircleIntersection(mac2, mac3, points);

        /**
        for (int i = 0; i < points.size() ; i++){
            System.out.println("x: " + points.get(i)[0] + "     y: " + points.get(i)[1]);
        }
        */

        if(points.size() > 0){
            // current minimum distance seen so far
            double currentMin = measureDistance(points.get(0)[0], points.get(0)[1], points.get(1)[0], points.get(1)[1]);
            double[] currentPoints = new double[4]; // two points are stored only, hence 4 coordinates
            double dist;
            for(int i = 0; i < points.size() - 1; i++){
                for(int j = i + 1; j < points.size(); j++) {
                    dist = measureDistance(points.get(i)[0], points.get(i)[1], points.get(j)[0], points.get(j)[1]);
                    if(dist < currentMin){
                        currentMin = dist;
                        currentPoints[0] = points.get(i)[0];
                        currentPoints[1] = points.get(i)[1];
                        currentPoints[2] = points.get(j)[0];
                        currentPoints[3] = points.get(j)[1];
                    }
                }
            }
            // update the location to an approximate location
            approximateLocation(currentPoints);
        }


    }


    /**
     * Replace the current location with the points added from the method intersection()
     * @param list is the list containing points
     */
    private void approximateLocation(double[] list){
        // take the average of x points
        ownLocation[0] = (list[0] + list[2]) / 2;

        // take the average of y points
        ownLocation[1] = (list[1] + list[3]) / 2;
    }


    /**
     * Calculates the intersection points between two circles and adds them in a list.
     *
     * @param mac1 the mac address of a selected beacon
     * @param points the list of all intersection points for the top 3 strongest signals
     * @ensures /old(points.size()) != points.size()
     */
    private void twoCircleIntersection(String mac1, String mac2, List<double[]> points){
        if (strongBeacons.size() >= 3){
            // parse the beacon information to double values
            double x1 = Double.parseDouble(String.valueOf(beacons.get(mac1.toLowerCase()).get(2)));
            double y1 = Double.parseDouble(String.valueOf(beacons.get(mac1.toLowerCase()).get(3)));

            //System.out.println("Coordinates from 1: (" + x1 +", " + y1+").");

            double x2 = Double.parseDouble(String.valueOf(beacons.get(mac2.toLowerCase()).get(2)));
            double y2 = Double.parseDouble(String.valueOf(beacons.get(mac2.toLowerCase()).get(3)));

            //System.out.println("Coordinates from 2: (" + x2 +", " + y2+").");

            // calculate the distance between circle's centers
            double dis = measureDistance(x1, y1, x2, y2); // Math.sqrt(Math.pow((x2 - x1), 2) + Math.pow((y2 - y1), 2)); //measureDistance(x2, y2, x1, y1);

            // retrieve the radii of the circles (aka. the calculated distance from us to the beacons)
            double r1 = strongBeacons.get(mac1); // Double.parseDouble(String.valueOf(beacons.get(mac1).get(5)));
            double r2 = strongBeacons.get(mac2); //Double.parseDouble(String.valueOf(beacons.get(mac1).get(5)));

            //System.out.println("distance r1: " + r1 + ", distance r2: " + r2);
            //System.out.println("measured distance: " + dis);

            // if the circles are overlapping, calculate the intersection points' coordinates
            //if (dis < r1 + r2  ) { // && dis > Math.abs(r1 - r2)
                double a = (dis + r1 + r2);
                double b = (-dis + r1 + r2);
                double c = (dis - r1 + r2);
                double d = (dis + r1 - r2);

                /**
                 *double a = (-dis + r1 - r2);
                 *                 double b = (-dis - r1 + r2);
                 *                 double c = (-dis + r1 + r2);
                 *                 double d = (dis + r1 + r2);
                 */

                double r = a * b * c * d;

                if (r < 0) {
                    r = r * (-1);
                }

                //System.out.println("a: " + a + ", b: " + b + ", c: "+ c + ", d: " + d + ", result = " + r);

                double commonArea =  0.25 * Math.sqrt(r);
                //System.out.println("common area: " + commonArea);

                // determine the coordinates of the intersection points using math formulas
                double pointX1 = (x1 + x2)/2 +  ((x2 - x1)*(r1*r1 - r2*r2))/(2*dis*dis) + 2*commonArea*((y1 - y2)/(dis*dis));
                double pointY1 = (y1 + y2)/2 +  ((y2 - y1)*(r1*r1 - r2*r2))/(2*dis*dis) - 2*commonArea*((x1 - x2)/(dis*dis));
                double pointX2 = (x1 + x2)/2 +  ((x2 - x1)*(r1*r1 - r2*r2))/(2*dis*dis) - 2*commonArea*((y1 - y2)/(dis*dis));
                double pointY2 = (y1 + y2)/2 +  ((y2 - y1)*(r1*r1 - r2*r2))/(2*dis*dis) + 2*commonArea*((x1 - x2)/(dis*dis));

                // add the newly determined points to the list of all intersection points for the 3 circles
                double[] newPoint1 = {pointX1, pointY1};
                double[] newPoint2 = {pointX2, pointY2};

                //System.out.println("New Points: \"" + pointX1 + ", "+ pointY1+ "\", \"" + pointX2 + ", "+ pointY2 +"\"");
                points.add(newPoint1);
                points.add(newPoint2);
            //}
        }

    }


    /**
     *  Takes the rssi value and calculated
     * @param rssi Received Signal Strength Indicator
     * @return the time that took from a packet to be received
     */
    public double timeOfArrival(int rssi){
        System.out.println("Here  >>>>"+rssi);
        double power = (-59 - rssi)/(10*2);
        System.out.println("power "+power);
        System.out.println("result: " +Math.pow(10, power));
        return Math.pow(10, power);
    }


    /**
     * Determine the distance between two 2D points.
     *
     * @requires x1 > 0 && y1 > 0 && x2 > 0 && y2 > 0
     */
    private double measureDistance(double x1, double y1, double x2, double y2){
        return Math.sqrt(Math.pow((x2 - x1), 2) + Math.pow((y2 - y1), 2));
    }



    // -------------------------------------------------------------------------------------
    // --------------------------         Beacon Notifier         --------------------------
    // -------------------------------------------------------------------------------------



    /**
     * Add a Range notifier that will indicates data from the scanned iBeacons
     */
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(this);
    }

    /**
     * After detecting an iBeacon, it reports it as a log() and calls the method which
     * should start collecting and displaying data from an iBeacon
     * @param region on it is scanning for iBeacons
     */
    @Override
    public void didEnterRegion(Region region) {
        Log.i(M_TAG, "An iBeacon entered the region");

        try {
            beaconManager.startRangingBeaconsInRegion(region);
        }
        catch (RemoteException e) {
            if (BuildConfig.DEBUG) Log.d("RangeNotifier", "Can't start ranging");
        }
    }


    /**
     * Method that takes care for something that need to be done when it exits the region
     * @param region on it is scanning for iBeacons
     */
    @Override
    public void didExitRegion(Region region) {

    }


    /**
     * Method that takes care for changing the state for such a region
     * @param state declared for this region
     * @param region on it is scanning for iBeacons
     */
    @Override
    public void didDetermineStateForRegion(int state, Region region) {

    }



    // -------------------------------------------------------------------------------------
    // --------------------------         Beacon Consumer         --------------------------
    // -------------------------------------------------------------------------------------


    /**
     *
     * @param beacons1 that were detected on the scanning
     * @param region on it was scanning for iBeacons
     */
    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons1, Region region) {
        if (beacons1.size() > 0) {
            Log.i(R_TAG, "iBeacons scanned:  " + beacons1.size());

            dialog.setMessage("Loading. Please wait...\n\niBeacons scanned: " + beacons1.size());
            scannedBeaconCounter = beacons1.size();
            List<String> sameFloor = new ArrayList<>();

            strongBeacons          = new HashMap<>();
            List<String> macs      = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            List<Double> tDistance = new ArrayList<>();


            //System.out.println("##################################\n");
            //System.out.println("########\tList Of Beacons\t########");

            for (Beacon b : beacons1) {
                Log.i(R_TAG, "iBeacon with: \"" + b.toString() + "\" is about " + b.getDistance() + " meters away.");

                //System.out.println(">> BT Address:      " + b.getBluetoothAddress().toLowerCase());  // always null
                //System.out.println(">> RSSI:         " + b.getRssi() +"\n");
                //System.out.println(">> First cycle:  " + b.getFirstCycleDetectionTimestamp()); // 1232534725
                //System.out.println(">> Second cycle: " + b.getLastCycleDetectionTimestamp());  //123456
                //System.out.println(">> ParseId :     " + b.getParserIdentifier());
                //System.out.println(">> NPa          " + b.getMeasurementCount());
                //System.out.println(">> Running Avr:  " + b.getRunningAverageRssi());
                //System.out.println(">> power:        " + b.getTxPower());
                //System.out.println("\n\n##################################\n\n");

                //CHECK IF THE BEACON'S MAC EXISTS ON THE MAP AND STORE IT
                //TODO : Check if it store it on lowercase
                String mc = b.getBluetoothAddress().toLowerCase();
                if(beacons.containsKey(mc)){
                    macs.add(mc);
                    //System.out.println("comparison between: [" + b.getDistance() + "] and [" + timeOfArrival(b.getRssi()) + "].");
                    double temp = 111139 * Math.cos(52.221539);
                    distances.add(b.getDistance()/temp);
                    tDistance.add(b.getDistance()/temp);
                }

            }
            //System.out.println("##################################\n\n");

            // Sort the lists in descending order to h
            Collections.sort(distances);
            Collections.reverse(distances);


            String nFloor = "";
            sameFloor = checkSameFloor(macs, distances, tDistance);
            nFloor = (String) beacons.get(sameFloor.get(0)).get(4);
            double nf = Double.valueOf(nFloor);
            FLOOR.setText("You are in floor number: " + (int) nf);


            System.out.println("######>>>>>>  " + nFloor);
            List<Integer> indexes = new ArrayList<>();

            for (String st : sameFloor){
                int id = macs.indexOf(st);
                indexes.add(id);
            }

            List<Double> newDistances = new ArrayList<>();
            List<Double> nDistance = new ArrayList<>();sameFloor.toArray();

            for (int id : indexes){
                double val = distances.get(id);
                newDistances.add(val);
                nDistance.add(val);
            }
            //System.out.println("###### >>> "+newDistances.size());
            Collections.sort(newDistances);
            Collections.reverse(newDistances);


            int id1 = 0;
            int id2 = 0;
            int id3 = 0;
            // DELETE IF IT IS NO NEEDED

            // It will put beacons with the strongest signal into hte hashmap strongBeacons
            if (sameFloor.size() >= 1){
                id1 = nDistance.indexOf(newDistances.get(0));
                strongBeacons.put(sameFloor.get(id1), newDistances.get(0));
                //System.out.println("> (1): " + macs.get(id1) + " with distance " + distances.get(0));
                if (sameFloor.size() >= 2){
                    id2 = nDistance.indexOf(newDistances.get(1));
                    strongBeacons.put(sameFloor.get(id2), newDistances.get(1));
                    //System.out.println("> (2): " + macs.get(id2) + " with distance " + distances.get(1));
                    if (sameFloor.size() >= 3){
                        id3 = nDistance.indexOf(newDistances.get(2));
                        strongBeacons.put(sameFloor.get(id3), newDistances.get(2));
                        //System.out.println("> (3): " + macs.get(id1) + " with distance " + distances.get(2));
                    }
                }
            }


            /**
            if (sameFloor.size() >= 1){
                id1 = tDistance.indexOf(macs.indexOf(sameFloor.get(0)));
                System.out.println("######>>>>>>  "+ id1);
                strongBeacons.put(sameFloor.get(0), tDistance.get(id1));
                //System.out.println("> (1): " + macs.get(id1) + " with distance " + distances.get(0));
                if (distances.size() >= 2){

                    id2 =tDistance.indexOf(sameFloor.get(1));
                    strongBeacons.put(sameFloor.get(1), distances.get(id2));
                    //System.out.println("> (2): " + macs.get(id2) + " with distance " + distances.get(1));
                    if (distances.size() >= 3){
                        id3 = tDistance.indexOf(sameFloor.);
                        strongBeacons.put(sameFloor.get(3), distances.get(id2));
                        //System.out.println("> (3): " + macs.get(id1) + " with distance " + distances.get(2));
                    }
                }
            }*/

            intersection(macs.get(id1), macs.get(id2), macs.get(id3));
            if ( ownLocation[1] != 0 && ownLocation[0] != 0){
                if(nMeasures > 1){
                    deleteFirst();
                    locations.add(ownLocation);
                    double[] avgLocation = new double[2];
                    avgLocation = averageLocation(locations);


                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Hide the progress bar and its text view until start scanning
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                                dialog.dismiss();
                                //relativeLayout1.setVisibility(View.VISIBLE);
                                //relativeLayout2.setVisibility(View.VISIBLE);

                                //progressBar.setVisibility(View.GONE);
                                //pbTextView.setVisibility(View.GONE);
                                //pbTextInfo.setVisibility(View.GONE);
                            }
                        }
                    }, 300);

                    drawPosition(avgLocation[0], avgLocation[1]);
                    System.out.println("#####>>" + avgLocation[0] + "||" + avgLocation[1]);
                    System.out.println("#####>>" + ownLocation[0] + "||" + ownLocation[1]);
                    System.out.println(">> " + toMeters(ownLocation[0], locations.get(1)[0],ownLocation[1], locations.get(1)[1]));
                }else{
                    drawPosition(ownLocation[0], ownLocation[1]);
                    locations.add(ownLocation);
                }
                nMeasures++;
            }

        }
    }
    /**
     * Calculate the avergare location of the last 5 live locations
     * @return
     */
    public double[] averageLocation(List<double[]> last5 ){
        double[] avg = new double[2];
        int size = last5.size();
        double x = 0;
        double y = 0;
        for (double[] loc : last5){
            x += loc[0];
            y += loc[1];
        }
        x = x/size;
        y = y/size;
        avg[0] = x;
        avg[1] = y;
        return avg;
    }
    public void deleteFirst(){
        locations.remove(0);
    }

    /**
     * Calculation to latitud or longitud in meters
     *
     */

    public double toMeters(double x1, double x2, double y1, double y2){
        double R = 6378.137;
        double x = x2 * Math.PI / 180 - x1* Math.PI / 180;
        double y = y2 * Math.PI / 180 - y1* Math.PI / 180;
        double a = Math.sin(x/2) * Math.sin(x/2) +
                Math.cos(x1 * Math.PI / 180) * Math.cos(x2 * Math.PI / 180) *
                        Math.sin(y/2) * Math.sin(y/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c;
        return d *1000;
    }

    /**
     * Algorithm to check in we are in the same room
     */
    public List<String> checkSameFloor(List<String> mac, List<Double> distances, List<Double> tDistances){
        List<String> f1 = new ArrayList<>();
        List<String> f2 = new ArrayList<>();

        if(mac.size() ==2 ){
            String mac1 = mac.get(tDistances.indexOf(distances.get(0)));
            if(beacons.get(mac1).get(4) == beacons.get(mac.get(tDistances.indexOf(distances.get(1)))).get(4)){
                return mac;
            }else{
                f1.add(mac1);
                return f1;
            }
        }else if(mac.size() == 3){
            String mac1 = mac.get(tDistances.indexOf(distances.get(0)));
            String mac2 = mac.get(tDistances.indexOf(distances.get(1)));
            String mac3 = mac.get(tDistances.indexOf(distances.get(2)));

            if(beacons.get(mac1).get(4) == beacons.get(mac2).get(4) &&
            beacons.get(mac1).get(4)== beacons.get(mac3).get(4)){
                f1 = mac;
            }else if(beacons.get(mac1).get(4) == beacons.get(mac2).get(4) ){
                f1.add(mac1);
                f1.add(mac2);
            }else if(beacons.get(mac1).get(4) == beacons.get(mac3).get(4) ){
                f1.add(mac1);
                f1.add(mac3);
            }else if(beacons.get(mac2).get(4) == beacons.get(mac3).get(4)){
                f1.add(mac2);
                f1.add(mac3);
            }else{
                f1.add(mac1);
            }
            return f1;

        }else if(mac.size() >3){
            String mac1 = mac.get(tDistances.indexOf(distances.get(0)));
            String floor = (String) beacons.get(mac1).get(4);
            int full = 0;
            boolean isFull = false;
            for (int i = 0; i < mac.size(); i++){
                String fMac = mac.get(tDistances.indexOf(distances.get(i)));
                if(!isFull){
                    if (floor == beacons.get(fMac).get(4)){
                        f1.add(fMac);
                        full ++;
                        if (full == 3){
                            isFull = true;
                        }
                    }else{
                        f2.add(fMac);
                    }
                }else if(isFull){
                    return f1;
                }else {
                    if(f2.isEmpty()){
                        return f1;
                    }else {
                        if(f1.size() > f2.size()){
                            return f1;
                        }else if (f2.size() > f1.size()){
                            return f2;
                        }else{
                            return f1;
                        }
                    }
                }
            }
        }
        return mac;
    }

    /***
     * Method to check and return a list of the beacons list
     * RETURNS A LIST OF BEACONS THAT EXIST IN THE LIST ( LOWERCASE )
     */
    public List<String> beaconsOnList(List<String> mac){
        List<String> onList  = new ArrayList<>();
        for(String address : mac){
            if(beacons.containsKey(address.toLowerCase())){
                onList.add(address.toLowerCase());
            }
        }
        return onList;
    }

    /**
     * Method that call a super function when the app is on pause
     */
    @Override
    protected void onPause() {
        super.onPause();
    }


    /**
     * Method that call a super function when it needs to be resumed
     */
    @Override
    protected void onResume() {
        super.onResume();
    }


    /**
     * Method that call a super function when the app was closed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.removeAllRangeNotifiers();
        beaconManager.removeAllMonitorNotifiers();
        beaconManager.unbind(this);
        beaconManager.isBound(this);
        nMeasures = 0;
        locations.clear();
    }
}