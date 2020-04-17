package poll_rails_game

object RailsBridge {
  val RAILS_JAR_LOCATION = System.getenv("RAILS_JAR_LOCATION")

  //TODO should probably verify that it actually exists
  def verifyEnvironment(): List[String] =
    if (Option(RAILS_JAR_LOCATION).isEmpty) List("Must set RAILS_JAR_LOCATION") else List()

  //TODO should return an error code etc
  def run(gameSave: String, screenshotDir: String): Unit =
    os.proc("java", "-cp", RAILS_JAR_LOCATION, "net.sf.rails.util.PrintGame", screenshotDir, gameSave).call()
}

//TODO seems more ideal to break positionTitle down into OR, round, player, railroad (if applicable)
case class GameInformation(positionTitle: String, images: List[String])