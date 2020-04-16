package poll_rails_game

object RailsBridge {
  def verifyEnvironment(): List[String]= {
    //TODO this needs to make sure that the required files are there eg the jar we need to run
    List()
  }

  def run(file: String, screenshotDir: String): Unit = {

  }
}

//TODO seems more ideal to break positionTitle down into OR, round, player, railroad (if applicable)
case class GameInformation(positionTitle: String, images: List[String])