package example.scalaglue

import cucumber.api.Scenario
import cucumber.api.scala.{ScalaDsl, EN}

class ScalaSteps extends ScalaDsl with EN {

  val greeter = example.Greeter.instance()

  When("""^I leave the room$""") {
    greeter.leave()
  }

  Before("@wip") { scenario: Scenario =>

  }

}