package poll_rails_game

object Main {
  def main(args: Array[String]): Unit = {
    val ACCESS_TOKEN = System.getenv("RAILS_POLL_ACCESS_TOKEN")
    //TODO proper logging
    //TODO use proper argument handling and pass as an argument?
    if (ACCESS_TOKEN == null)
      throw new IllegalArgumentException("need to set RAILS_POLL_ACCESS_TOKEN environment variable")


    Dropbox.checkInfo(ACCESS_TOKEN)
  }
}
