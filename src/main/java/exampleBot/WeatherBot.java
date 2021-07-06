package exampleBot;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import exampleBot.weather.OpenWeatherMap;
import org.json.JSONObject;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


public class WeatherBot extends TelegramLongPollingBot {
    private static final Logger log = Logger.getLogger(WeatherBot.class.getName());
    private static final String url = "http://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=12ad7045fe16bf1610798488c985fb07";
    private List<Long> currentSubscribeUsers = new ArrayList<>();


    @Override
    public String getBotToken() {
        return "1260313315:AAGbN9j2oOeN23qSeyoI1BYfHVRCPxiD94s";
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message msg = update.getMessage();
        SendMessage newMsg = new SendMessage();
        newMsg.enableMarkdown(true);
        newMsg.setChatId(msg.getChatId().toString());
        newMsg.setReplyToMessageId(msg.getMessageId());
        if(msg.hasLocation()) {
            if(currentSubscribeUsers.contains(msg.getChatId())) {
                Map<Long, FollowerLocation> followerMap = null;
                try {
                    followerMap = readFollowerFromFile();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                if((followerMap != null) && (followerMap.containsKey(msg.getChatId()))) {
                    deleteFollowerFromFile(msg.getChatId());
                    addToFileFollower(msg.getChatId(), msg.getLocation().getLatitude(), msg.getLocation().getLongitude());
                    currentSubscribeUsers.remove(msg.getChatId());
                    newMsg.setText("Ура, в изменили место");
                    log.info(String.format("New place %s for old follower %s.", msg.getLocation().toString(),msg.getChatId().toString()));
                }
                else {
                    addToFileFollower(msg.getChatId(), msg.getLocation().getLatitude(), msg.getLocation().getLongitude());
                    currentSubscribeUsers.remove(msg.getChatId());
                    newMsg.setText("Ура, теперь вы подписчик!");
                    log.info(String.format("Add new follower: place %s follower %s.", msg.getLocation().toString(),msg.getChatId().toString()));
                }
            }
            else {
                newMsg.setText(sendWeather(msg.getLocation().getLongitude(), msg.getLocation().getLatitude()));
            }

        }
        else {
            switch (msg.getText().toLowerCase()) {
                case "/start" :
                    newMsg.setText("Отправьте вашу локацию");
                    log.info("Msg: Please, send your location");
                    break;
                case "/subscribe" :
                    currentSubscribeUsers.add(msg.getChatId());
                    newMsg.setText("Отправьте вашу локацию, для которой Вы хотите ежедневно получать рассылку");
                    log.info("Msg: please, send your location");
                    break;
                case "/unsubscribe" :
                    Map<Long, FollowerLocation> followerMap = null;
                    try {
                        followerMap = readFollowerFromFile();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    if(followerMap != null && followerMap.containsKey(msg.getChatId())) {
                        newMsg.setText("нам очень грустно с вами расставаться");
                        log.info("Msg: goodbye, my dear friend");
                        //followerMap.remove(msg.getChatId());
                        deleteFollowerFromFile(msg.getChatId());
                        log.info(String.format("remove follower %s", msg.getChatId().toString()));
                    }
                    else {
                        newMsg.setText("Вы не были подписаны");
                        log.info("Msg: you are not right");
                    }
                    break;
                case "/changeloc" :
                    newMsg.setText("Отправьте вашу новую локацию");
                    log.info("Msg: new loc");
                    if (!currentSubscribeUsers.contains(msg.getChatId()))
                        currentSubscribeUsers.add(msg.getChatId());
                    break;
                default:
                    newMsg.setText("Моя твоя не понимать!");
                    log.info("Msg: i don't understand you");
                    break;

            }
        }
        try {
                execute(newMsg);
        } catch (TelegramApiException e) {
                e.printStackTrace();
        }
    }

    private String sendWeather(float lon, float lat) {
            log.info(String.format("Location: longitude = %f, latitude = %f", lon, lat));
            return findInfoByLocation(lon, lat);
    }

    private String findInfoByLocation (float lon, float lat) {
        URL getURL = null;
        StringBuilder strWeather = new StringBuilder();
        try {
            getURL = new URL(String.format(url,
                                            URLEncoder.encode(String.valueOf(lat), StandardCharsets.UTF_8.toString()),
                                            URLEncoder.encode(String.valueOf(lon), StandardCharsets.UTF_8.toString())));
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        log.info(getURL.toString());
        try {
            HttpURLConnection getUrlConnect = (HttpURLConnection) getURL.openConnection();
            getUrlConnect.setRequestMethod("GET");
            getUrlConnect.setRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream getWeatherStream = getUrlConnect.getInputStream();
            Gson gson = new Gson();
            String myJson = CharStreams.toString(new InputStreamReader(getWeatherStream));
            log.info(myJson);
            OpenWeatherMap weather = gson.fromJson(myJson, OpenWeatherMap.class);
            if (weather == null)
                log.info("i cannot find a weather");
            else if(weather.getWeather() != null) {
                    strWeather.append(String.format("Погода: %s\n", weather.getWeather().get(0).getDescription().toString()));
                    strWeather.append(String.format("Температура: %f\n", weather.getMain().getTemp() - 273.15));
                    strWeather.append(String.format("Ощущается как: %f\n", weather.getMain().getFeelsLike() - 273.15));
                    strWeather.append(String.format("Минимальная температура: %f\n", weather.getMain().getTempMin() - 273.15));
                    strWeather.append(String.format("Максимальная температура: %f\n", weather.getMain().getTempMax() - 273.15));
                    strWeather.append(String.format("Давление (мм рт. ст): %f\n", weather.getMain().getPressure() * 0.75006375541921));
                    strWeather.append(String.format("Влажность: %s\n", weather.getMain().getHumidity().toString()));
                    strWeather.append(String.format("Скорость ветра (м/сек): %s\n", weather.getWind().getSpeed().toString()));
                  }
                  else {
                    log.info("i cannot find a weather");
                    strWeather.append("Да просто посмотри в окно!");
                  }/*

            JSONObject weather = new JSONObject((new JSONObject(myJson)).optString("main"));
            if (weather == null) {
                log.info("i cannot find a weather");
                strWeather.append("Посмотри в окно, не ленись!");
            }
            else {
                strWeather.append(String.format("Температура: %.1f\u2103\n", Float.parseFloat(weather.optString("temp")) - 273.15));
                strWeather.append(String.format("Ощущается как: %.1f\u2103\n", Float.parseFloat(weather.optString("feels_like")) - 273.15));
                strWeather.append(String.format("Минимальная температура: %.1f\u2103\n", Float.parseFloat(weather.optString("temp_min")) - 273.15));
                strWeather.append(String.format("Максимальная температура: %.1f\u2103\n", Float.parseFloat(weather.optString("temp_max")) - 273.15));
                strWeather.append(String.format("Давление (мм рт. ст): %.1f\n", Float.parseFloat(weather.optString("pressure")) * 0.75006375541921));
                strWeather.append(String.format("Влажность: %s%%\n", Float.parseFloat(weather.optString("humidity"))));
            }*/
            getWeatherStream.close();

        } catch (IOException e) {
            log.info("IOException");
            e.printStackTrace();
        }
        log.info(strWeather.toString());
        return strWeather.toString();
    }

    public void dailyMailyBot() {
        Map<Long, FollowerLocation> followerMap = null;
        try {
            followerMap = readFollowerFromFile();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        for (Long chatId : followerMap.keySet()) {
                SendMessage newMsg = new SendMessage();
                newMsg.setChatId(chatId);
                //newMsg.setText("test");
                newMsg.setText(sendWeather(followerMap.get(chatId).getLon(),followerMap.get(chatId).getLat()));
                log.info(String.format("DailyMailyBot %s", chatId.toString()));
                try {
                    execute(newMsg);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
    }



    @Override
    public String getBotUsername() {
        return "WeatherSmileBot";
    }

    static public Map<Long, FollowerLocation> readFollowerFromFile() throws FileNotFoundException {
        FileReader fr = new FileReader("src/main/resources/dailyMaily.txt");
        try (BufferedReader br = new BufferedReader(fr)){
            String line;
            String[] words = new String[3];
            Map<Long, FollowerLocation> followers = new HashMap<>();
            br.readLine();
            while ((line = br.readLine()) != null) {
                words = line.split(",");
                followers.put(Long.valueOf(words[0]),new FollowerLocation(Float.parseFloat(words[1]), Float.parseFloat(words[2])));
            }
            fr.close();
            return followers;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static public void addToFileFollower(Long chatId, float lat, float lon) {
        String filename = "src/main/resources/dailyMaily.txt";
        log.info("src/main/resources/dailyMaily.txt");
        File file = new File(filename);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename, true))) {
            bw.write(chatId.toString() + "," + lat + "," + lon);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static  public  void deleteFollowerFromFile(Long chatId) {
        Map<Long, FollowerLocation> followers = null;
        try {
            followers = readFollowerFromFile();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String filename = "src/main/resources/dailyMaily.txt";
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename, false))) {
            bw.write("chatId,lat,lon:");
            bw.newLine();
            if(followers != null )
                for (Long chat : followers.keySet()) {
                    if (!chat.equals(chatId)) {
                        bw.write(chatId.toString() + "," + followers.get(chat).getLat() + "," + followers.get(chat).getLon());
                        bw.newLine();
                    }
                }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        ApiContextInitializer.init();
        TelegramBotsApi botapi = new TelegramBotsApi();
        try {
            WeatherBot myBot = new WeatherBot();
            botapi.registerBot(myBot);
            DailyThread dailyThread = new DailyThread(myBot);
            dailyThread.start();

        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }

    }

}
