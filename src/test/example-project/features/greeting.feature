Feature: Greetings

  Scenario: Can set system properties
    Then the system property "customSystemProperty" is set to "avalue"

  Scenario: My computer greets the world
    Given I am not logged in
    When I say "Hello, computer."
    Then the computer should say, "Hello, World!"

  Scenario: My computer greets me by name
    Given I am logged in as "Bob"
    When I say "Hello, computer."
    Then the computer should say, "Hello, Bob!"

  @wip
  Scenario: My computer greets me by name
    Given I am logged in as "Bob"
    When I say "Hello, computer."
    And I wait 1 second
    Then the computer should say, "Hello, Bob!"

  Scenario: My computer says goodbye
    Given I am logged in as "Bob"
    When I leave the room
    Then the computer should say, "Goodbye, Bob!"