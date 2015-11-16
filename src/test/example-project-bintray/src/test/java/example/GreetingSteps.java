package example;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.lang.InterruptedException;

import static org.junit.Assert.assertEquals;

public class GreetingSteps {

	private Greeter greeter = new Greeter();
	private String response;

	@Given("^I am not logged in$")
	public void I_am_not_logged_in() throws Throwable {
		greeter.setLoginName(null);
	}

	@When("^I say \"([^\"]*)\"$")
	public void I_say(String whatISaid) throws Throwable {
		response = greeter.respond(whatISaid);
	}

	@Given("^I am logged in as \"([^\"]*)\"$")
	public void I_am_logged_in_as(String loginName) throws Throwable {
		greeter.setLoginName(loginName);
	}

	@Then("^the computer should say, \"([^\"]*)\"$")
	public void the_computer_should_say(String computersResponse) throws Throwable {
		assertEquals(computersResponse,  response);
	}

	@When("^I wait (\\d+) seconds?$")
	public void I_wait_seconds(int seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException ignored) {
		}
	}

	@Then("^the system property \"([^\"]*)\" is set to \"([^\"]*)\"$")
	public void the_system_property_is_set_to(String name, String expectedValue) {
		assertEquals(expectedValue, System.getProperty(name));
	}
}
