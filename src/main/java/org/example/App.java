package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App {
    private static HashMap<String, String> envMap= new HashMap<>();

    public static void main( String[] args ) {
        System.out.println( "START" );

        try {
            loadEnv();

            String userFilePath = envMap.get("USER_DATA");
            List<String> users = generateList(userFilePath);
            List<String> csvHotelUsers = new ArrayList<>();
            csvHotelUsers.add("email,hotelId,hotelName");

            List<String> csvHotelGroupUsers = new ArrayList<>();
            csvHotelGroupUsers.add("email,hotelGroup,groupName");

            for (String user : users) {
                //Fetch User to Hotel
                fetchUserHotel(user, csvHotelUsers, "HOTEL_ID");
                fetchUserHotel(user, csvHotelGroupUsers, "HOTEL_GROUP");

                //Fetch User uuid
                String uuid = fetchUserUuid(user);

                //Change Password
                if (uuid != null && !uuid.isEmpty()) {
                    sendRequestChangePassword(uuid);
                }
            }

            File outPutDir = new File("output");
            if (!outPutDir.exists()) {
                outPutDir.mkdirs();
            }

            writeCsv(csvHotelUsers, outPutDir, "user_to_hotel.csv");
            writeCsv(csvHotelGroupUsers, outPutDir, "user_to_hotel_group.csv");

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println( "DONE" );
    }

    private static void loadEnv() throws Exception {
        File filePath = new File(".env-staging");
        if (!filePath.exists()) {
            throw new Exception("Please create .env file");
        }

        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            fis = new FileInputStream(filePath);
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);

            String data = "";
            while ((data = br.readLine()) != null) { // use if for reading just 1 line
                String[] dataSplit = data.split("=");
                envMap.put(dataSplit[0], dataSplit[1]);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if (br != null) {
                    br.close();
                }

                if (isr != null) {
                    isr.close();
                }

                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
    private static void fetchUserHotel(String email, List<String> csvHotel, String scope) throws IOException {
        JSONObject jsonRequest = new JSONObject();

        JSONObject jsonData = new JSONObject();
        jsonData.put("username", email);
        jsonData.put("scopeType", scope);
        jsonRequest.put("data", jsonData);

        JSONObject jsonContext = new JSONObject();
        jsonContext.put("hnetSession",envMap.get("TERA_HNET_SESSION"));
        jsonContext.put("hnetLifetime",envMap.get("TERA_HNET_LIFETIME"));
        jsonRequest.put("context", jsonContext);

        URL url = new URL(envMap.get("TERA_URL")+"/v1/account/getHotelAccountScope");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        con.setDoOutput(true);
        con.setDoInput(true);
        con.connect();

        con.getOutputStream();
        try(OutputStream os = con.getOutputStream()) {
            os.write(jsonRequest.toString().getBytes());
            os.flush();
        }

        BufferedReader in = null;
        if (con.getResponseCode() >= 400) {
            in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        }
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }

        JSONObject responseJson = new JSONObject(content.toString());
        String status = responseJson.getString("status");

        if (status.equalsIgnoreCase("SUCCESS")) {
            JSONObject jsonDataResponse = responseJson.getJSONObject("data");
            JSONArray hotels = jsonDataResponse.getJSONArray("_hotelAccountScopeSummaries");

            for (int i = 0; i < hotels.length(); i++) {
                JSONObject jsonHotel = hotels.getJSONObject(i);
                String userEmail = jsonHotel.getString("username");
                String id = "";
                String name = "";
                if (scope.equalsIgnoreCase("HOTEL_ID")) {
                    id = jsonHotel.getString("hotelId");
                    name = jsonHotel.getString("hotelName");
                } else if (scope.equalsIgnoreCase("HOTEL_GROUP")) {
                    id = jsonHotel.getString("hotelGroupId");
                    name = jsonHotel.getString("hotelGroupName");
                }
                String csv = userEmail+","+id+","+name;
                csvHotel.add(csv);
            }

        }
        in.close();
    }

    private static String fetchUserUuid(String email) throws IOException {
        String result = null;
        URL url = new URL(envMap.get("ATH_URL")+"/pool/"+envMap.get("USER_POOL")+"/users?limit=30&filter="+email);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer "+envMap.get("ATH_TOKEN"));

        con.setDoInput(true);
        con.connect();

        BufferedReader in = null;
        if (con.getResponseCode() >= 400) {
            in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        }
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        JSONObject responseJson = new JSONObject(content.toString());
        JSONArray itemsJson = responseJson.getJSONArray("items");
        if (!itemsJson.isEmpty()) {
            JSONObject jsonUser = itemsJson.getJSONObject(0);
            result = jsonUser.getString("id");
        }
        in.close();
        return result;
    }

    private static void sendRequestChangePassword(String userUuid) throws IOException {
        JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("password","Tr4v3l0k420240123!!!");
        String stringUrl = envMap.get("ATH_URL")+"/pool/"+envMap.get("USER_POOL")+"/users/"+userUuid+"/password";
        URL url = new URL(stringUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        con.setRequestProperty("Authorization", "Bearer "+envMap.get("ATH_TOKEN"));
        con.setDoOutput(true);
        con.setDoInput(true);
        con.connect();

        con.getOutputStream();
        try(OutputStream os = con.getOutputStream()) {
            os.write(jsonRequest.toString().getBytes());
            os.flush();
        }

        BufferedReader in = null;
        if (con.getResponseCode() >= 400) {
            in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        }
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
    }

    private static List<String> generateList(String filePath) {
        List<String> result = new ArrayList<>();
        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            fis = new FileInputStream(filePath);
            isr = new InputStreamReader(fis);
            br = new BufferedReader(isr);

            String data = "";
            while ((data = br.readLine()) != null) { // use if for reading just 1 line
                result.add(data);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if (br != null) {
                    br.close();
                }

                if (isr != null) {
                    isr.close();
                }

                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        return result;
    }

    private static void writeCsv(List<String> csvHotelUsers, File outPutDir, String fileName) {
        File file = new File(outPutDir, fileName);
        if (file.exists()) {
            file.delete();
        }

        try (PrintWriter pw = new PrintWriter(file)) {
            csvHotelUsers.forEach(pw::println);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
