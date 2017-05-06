import sbt._
import Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin
import ScalaJSPlugin.autoImport.jsDependencyManifest

object ExternalCompile {

  private val isWindows =
    System.getProperty("os.name").toLowerCase().indexOf("win") >= 0

  val scalaJSExternalCompileConfigSettings: Seq[Setting[_]] = inTask(compile)(
      Defaults.runnerTask
  ) ++ Seq(
      fork in compile := true,
      trapExit in compile := true,
      javaOptions in compile += "-Xmx512M",

      compile := {
        val inputs = (compileInputs in compile).value
        val inputsOptions = inputs.options
        import inputsOptions._

        val s = streams.value
        val logger = s.log
        val cacheDir = s.cacheDirectory

        // Discover classpaths

        def cpToString(cp: Seq[File]) =
          cp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

        // XXX
        type CompilersAccess = { def scalac(): xsbti.compile.CachedCompilerProvider }
        val compilerCp = inputs.compilers.asInstanceOf[CompilersAccess].scalac.scalaInstance.allJars
        val cpStr = cpToString(classpath)

        // List all my dependencies (recompile if any of these changes)

        val allMyDependencies = classpath filterNot (_ == classesDirectory) flatMap { cpFile =>
          if (cpFile.isDirectory) (cpFile ** "*.class").get
          else Seq(cpFile)
        }

        // Compile

        val cachedCompile = FileFunction.cached(cacheDir / "compile",
            FileInfo.lastModified, FileInfo.exists) { (dependencies: Set[File]) =>

          logger.info(
              "Compiling %d Scala sources to %s..." format (
              sources.size, classesDirectory))

          if (classesDirectory.exists)
            IO.delete(classesDirectory)
          IO.createDirectory(classesDirectory)

          val sourcesArgs = sources.map(_.getAbsolutePath()).toList

          /* run.run() below in doCompileJS() will emit a call to its
           * logger.info("Running scala.tools.nsc.scalajs.Main [...]")
           * which we do not want to see. We use this patched logger to
           * filter out that particular message.
           */
          val patchedLogger = new Logger {
            def log(level: Level.Value, message: => String) = {
              val msg = message
              if (level != Level.Info ||
                  !msg.startsWith("Running scala.tools.nsc.Main"))
                logger.log(level, msg)
            }
            def success(message: => String) = logger.success(message)
            def trace(t: => Throwable) = logger.trace(t)
          }

          def doCompile(sourcesArgs: List[String]): Unit = {
            val run = (runner in compile).value
            run.run("scala.tools.nsc.Main", compilerCp,
                "-cp" :: cpStr ::
                "-d" :: classesDirectory.getAbsolutePath() ::
                scalacOptions ++:
                sourcesArgs,
                patchedLogger) getOrElse sys.error("Failed to compile")
          }

          /* Crude way of overcoming the Windows limitation on command line
           * length.
           */
          if ((fork in compile).value && isWindows &&
              (sourcesArgs.map(_.length).sum > 1536)) {
            IO.withTemporaryFile("sourcesargs", ".txt") { sourceListFile =>
              IO.writeLines(sourceListFile, sourcesArgs)
              doCompile(List("@"+sourceListFile.getAbsolutePath()))
            }
          } else {
            doCompile(sourcesArgs)
          }

          // Output is all files in classesDirectory
          (classesDirectory ** AllPassFilter).get.toSet
        }

        cachedCompile((sources ++ allMyDependencies).toSet)

        // We do not have dependency analysis when compiling externally
        // TODO: non internal
        sbt.internal.inc.Analysis.Empty
      },

      // Make sure jsDependencyManifest runs after compile, otherwise compile
      // might remove the entire directory afterwards.
      jsDependencyManifest := jsDependencyManifest.dependsOn(compile).value
  )

  val scalaJSExternalCompileSettings = (
      inConfig(Compile)(scalaJSExternalCompileConfigSettings) ++
      inConfig(Test)(scalaJSExternalCompileConfigSettings)
  )

}
