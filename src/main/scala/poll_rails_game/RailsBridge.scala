package poll_rails_game

object RailsBridge {
  val RAILS_JAR_LOCATION = System.getenv("RAILS_JAR_LOCATION")
  val PATH_TO_FX = System.getenv("PATH_TO_FX")

  //TODO should probably verify that it actually exists
  def verifyEnvironment(): List[String] =
    (if (Option(RAILS_JAR_LOCATION).isEmpty) List("Must set RAILS_JAR_LOCATION") else List()) ++
    (if (Option(PATH_TO_FX).isEmpty) List("Must set PATH_TO_FX") else List())

  //TODO should return an error code etc
  //TODO probably the most important thing at this point is ensure that if this fails, things are handled
  //  gracefully. gracefully here likely means:
  //    - easily recovered logs
  //    - I am alerted
  //    - the whole app does not crash (this also involved downstream pieces)
  def run(gameSave: String, screenshotDir: String): Boolean = {
    val result = os.proc(
      "java",
      "--module-path", PATH_TO_FX, "--add-modules", "javafx.controls,javafx.swing",
      "-cp", RAILS_JAR_LOCATION,
      "net.sf.rails.util.PrintGame", screenshotDir, gameSave
    ).call(stdout = os.Inherit, stderr = os.Inherit)
    //TODO should we consider doing more here around the state of things?
    //  at the very least, should probably do some logging
    val successful = result.exitCode == 0
    if (!successful) {
      val delimiter = "=================="
      val body = List(s"Game save: $gameSave", "", "stdout", delimiter, result.out.text(), delimiter, "", "stderr", delimiter, result.err.text(), delimiter)
      EmailContent("jcoveney+poll_rails_game@gmail.com", "jcoveney@gmail.com", "Errors detected", body.mkString("\n"), Map()).makeRequest()
    }
    successful
  }
}

//TODO seems more ideal to break positionTitle down into OR, round, player, railroad (if applicable)
case class GameInformation(positionTitle: String, images: List[String])