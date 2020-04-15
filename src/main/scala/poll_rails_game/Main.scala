package poll_rails_game

object Main {
  def main(args: Array[String]): Unit = {
    val ACCESS_TOKEN = Option(System.getenv("RAILS_POLL_ACCESS_TOKEN")).getOrElse {
      //TODO proper logging
      //TODO use proper argument handling and pass as an argument?
      throw new IllegalArgumentException("need to set RAILS_POLL_ACCESS_TOKEN environment variable")
    }

    Dropbox.longpoll("", ACCESS_TOKEN) { changes =>
      changes.foreach { println(_) }
    }
  }
}
