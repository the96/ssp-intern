scalaVersion := "2.12.6"
scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlint")
libraryDependencies ++= Seq(
	"io.circe" %% "circe-core" % "0.9.3",
	"io.circe" %% "circe-generic" % "0.9.3",
	"io.circe" %% "circe-parser" % "0.9.3",
	"org.scalaj" %% "scalaj-http" % "2.4.1",
)
