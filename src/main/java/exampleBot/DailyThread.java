package exampleBot;

public class DailyThread extends Thread{
    private WeatherBot bot;

    public DailyThread(WeatherBot bot) {
        this.bot = bot;
    }

    @Override
    public void run() {
        while (true) {
            bot.dailyMailyBot();
            try {
                //sleep(200);
                sleep(24 * 60 * 60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
