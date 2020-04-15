package poll_rails_game

import java.io.File

object GameWatchConf {
  val GAME_ROOT = "/poll_rails_game"

  //TODO the value should probably be a case class
  val games = Map(
    "train game example" -> GameWatchConf("poll_rails_game_testing@googlegroups.com", "2020 Test Game Q")
  )
}
case class GameWatchConf(email: String, gameName: String) {
  //TODO does this need to be pulled out?
  //TODO is there a way to make this not rely on them donig stuff, and instead more data in data out?
  // This needs to run the hacked rails to save screenshots to the given directory
  // It also needs to send the email
  def processEvent(md: ChangeMetadata, screenshotDir: String): Unit =
    md match {
      case fmd: FileMD =>
        val file = fmd.pathLower.getOrElse { throw new IllegalArgumentException(s"Shouldn't be possible for a watched event not to have a path: $md")}
        // Run the hacked rails
        RailsBridge.run(file, screenshotDir)
      case _ =>
    }
}

object Main {
  // If the file is on the game path, will return Some(file), otherwise nothing
  private def getGameName(path: String): Option[String] = {
    val file = new File(path)
    Option(file.getParent()).flatMap { parent =>
      if (parent.equals(GameWatchConf.GAME_ROOT)) Some(file.getName())
      else getGameName(parent)
    }
  }

  def main(args: Array[String]): Unit = {
    val ACCESS_TOKEN = Option(System.getenv("RAILS_POLL_ACCESS_TOKEN")).getOrElse {
      //TODO proper logging
      //TODO use proper argument handling and pass as an argument?
      throw new IllegalArgumentException("need to set RAILS_POLL_ACCESS_TOKEN environment variable")
    }

    Dropbox.longpoll(GameWatchConf.GAME_ROOT, ACCESS_TOKEN) { changes =>
      val watchedGames = GameWatchConf.games.keys.toSet
      val watchedChanges = changes.filter { change =>
        val isWatched = change.pathLower.flatMap { getGameName(_) }.filter { watchedGames.contains(_) }.nonEmpty
        //TODO logging
        if (isWatched) println(s"WATCHED: $change")
        else println(s"UNWATCHED: $change")
        isWatched
      }
    }

    // First, for every game I'm watching, need to get the files that already exist (for now punt on this)
    // Filter out games I am not watching (eg no game -> email map)
    //TODO what's the best way to dynamically reload configuration like that? For the time being just require a recompile, shouldn't be often
    // For every game, need a database of
    // - emails I have already sent
    // - the latest move whose email has been sent
    //TODO need to do what happens if a mistake is made and they roll back time
    // TO BEGIN WITH: punt on the database. It will only send emails if it is running (to start).
    //   It will send an email for every new file created on its watch
    //   (though could perhaps flag if a new file is back in time or something)

  }
}
