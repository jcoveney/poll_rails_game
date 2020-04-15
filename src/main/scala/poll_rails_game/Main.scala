package poll_rails_game

import java.io.File

object Conf {
  val GAME_ROOT = "/poll_rails_game"

  //TODO the value should probably be a case class
  val games = Map(
    "train game example" -> ("poll_rails_game_testing@googlegroups.com", "2020 Test Game Q")
  )
}

object Main {
  // If the file is on the game path, will return Some(file), otherwise nothing
  private def getGameName(path: String): Option[String] = {
    val file = new File(path)
    Option(file.getParent()).flatMap { parent =>
      if (parent.equals(Conf.GAME_ROOT)) Some(file.getName())
      else getGameName(parent)
    }
  }

  def main(args: Array[String]): Unit = {
    val ACCESS_TOKEN = Option(System.getenv("RAILS_POLL_ACCESS_TOKEN")).getOrElse {
      //TODO proper logging
      //TODO use proper argument handling and pass as an argument?
      throw new IllegalArgumentException("need to set RAILS_POLL_ACCESS_TOKEN environment variable")
    }

    Dropbox.longpoll(Conf.GAME_ROOT, ACCESS_TOKEN) { changes =>
      val watchedGames = Conf.games.keys.toSet
      changes.filter { change =>
        val isWatched = change.pathLower.flatMap { getGameName(_) }.filter { watchedGames.contains(_) }.nonEmpty
        //TODO logging
        if (isWatched) println(s"polled a change on a watched path: $change")
        else println(s"polled a change on an unwatched path: $change")
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
