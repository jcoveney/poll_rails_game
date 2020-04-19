package poll_rails_game

object RailsBridge {
  val RAILS_JAR_LOCATION = System.getenv("RAILS_JAR_LOCATION")
  val PATH_TO_FX = System.getenv("PATH_TO_FX")

  //TODO should probably verify that it actually exists
  def verifyEnvironment(): List[String] =
    (if (Option(RAILS_JAR_LOCATION).isEmpty) List("Must set RAILS_JAR_LOCATION") else List()) ++
    (if (Option(PATH_TO_FX).isEmpty) List("Must set PATH_TO_FX") else List())

  //TODO should return an error code etc
  def run(gameSave: String, screenshotDir: String): Unit =
    os.proc(
      "java",
      "--module-path", PATH_TO_FX, "--add-modules", "javafx.controls,javafx.swing",
      "-cp", RAILS_JAR_LOCATION,
      "net.sf.rails.util.PrintGame", screenshotDir, gameSave
    ).call(stdout = os.Inherit, stderr = os.Inherit)
}

//TODO seems more ideal to break positionTitle down into OR, round, player, railroad (if applicable)
case class GameInformation(positionTitle: String, images: List[String])