package cohapp;

public class Main {

  public static void main(String[] args) {

    if (args.length == 1) {
      String arg = args[0];

      CacheClient client = new CacheClient();

      if (arg.compareToIgnoreCase("load") == 0) {
        client.loadCache();
        return;
      }
      else if (arg.compareToIgnoreCase("validate") == 0) {
        client.validateCache();
        return;
      }
    }
    System.out.println("Param must be load or validate ");

  }
}
