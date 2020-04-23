scalaVersion := "2.13.1"
name := "poll_rails_game"
version := "0.1"
semanticdbEnabled := true // enable SemanticDB
semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version

scalacOptions ++= Seq("-Ywarn-unused")

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.2.1"

/*
OrganizeImports {
  expandRelative = true
  groupedImports = Explode
  groups = ["re:javax?\\.", "scala.", "*"]
  importSelectorOrder = Ascii
  removeUnused = true
}
*/

// https://mvnrepository.com/artifact/com.dropbox.core/dropbox-core-sdk
libraryDependencies += "com.dropbox.core" % "dropbox-core-sdk" % "3.1.3"
libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.0"
libraryDependencies += "com.lihaoyi" %% "upickle" % "1.1.0"
libraryDependencies += "com.sendgrid" % "sendgrid-java" % "4.4.8"
libraryDependencies += "commons-io" % "commons-io" % "2.6"

assemblyJarName in assembly := "poll_rails_game-assembly.jar"

mainClass in assembly := Some("poll_rails_game.Main")

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}