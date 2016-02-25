package org.anormcypher.async

import org.anormcypher.CypherParser._ // scalastyle:ignore underscore.import
import org.anormcypher.Cypher
import org.anormcypher.CypherRow
import org.anormcypher.NeoNode

class CypherParserAsyncSpec extends BaseAsyncSpec {

  // scalastyle:off line.size.limit
  override def beforeEach: Unit = {
    // initialize some test data
    Cypher("""create
      (us {type:"Country", name:"United States", code:"USA", tag:"anormcyphertest"}),
      (germany {type:"Country", name:"Germany", code:"DEU", population:81726000, tag:"anormcyphertest"}),
      (france {type:"Country", name:"France", code:"FRA", tag:"anormcyphertest", indepYear:1789}),
      (monaco {name:"Monaco", population:32000, type:"Country", code:"MCO", tag:"anormcyphertest"}),
      (english {type:"Language", name:"English", code:"EN", tag:"anormcyphertest"}),
      (french {type:"Language", name:"French", code:"FR", tag:"anormcyphertest"}),
      (german {type:"Language", name:"German", code:"DE", tag:"anormcyphertest"}),
      (arabic {type:"Language", name:"Arabic", code:"AR", tag:"anormcyphertest"}),
      (italian {type:"Language", name:"Italian", code:"IT", tag:"anormcyphertest"}),
      (russian {type:"Language", name:"Russian", code:"RU", tag:"anormcyphertest"}),
      france-[:speaks {official:true}]->french,
      france-[:speaks]->arabic,
      france-[:speaks]->italian,
      germany-[:speaks {official:true}]->german,
      germany-[:speaks]->english,
      germany-[:speaks]->russian,
      (proptest {
        name:"proptest", tag:"anormcyphertest", f:1.234, i:1234,
        l:12345678910, s:"s", arri:[1,2,3,4], arrs:["a","b","c"],
        arrf:[1.234,2.345,3.456]
      });
      """).apply()
  }
  // scalastyle:on line.size.limit

  override def afterEach: Unit = {
    // delete the test data
    Cypher("""
      MATCH (n) WHERE n.tag = "anormcyphertest"
      OPTIONAL MATCH (n)-[r]-()
      DELETE n, r""").apply()
  }

  // scalastyle:off multiple.string.literals
  "CypherParser" should "be able to parse a node" in {
    case class Country(name: String, node: NeoNode)
    val results = Cypher("""
        START n = node(*) WHERE n.type = 'Country'
        RETURN n.name AS name, n ORDER BY name desc""").
      async().
      futureValue.
      map { row => Country(row[String]("name"), row[NeoNode]("n")) }.
      toList
    results.head.name should equal ("United States")
    results.head.node.props should equal (Map("name" -> "United States",
                                              "type" -> "Country",
                                              "tag" -> "anormcyphertest",
                                              "code" -> "USA"))
  }
  // scalastyle:on multiple.string.literals

  it should "be able to parse into a single Long" in {
    val count: Long = Cypher("""
      START n = node(*)
      WHERE n.tag = 'anormcyphertest'
      RETURN count(n)""").asAsync(scalar[Long].single).futureValue
    count should equal (11) // scalastyle:ignore magic.number
  }

  it should "be able to parse a case class with a node" in {
    val results = Cypher("""
        START n = node(*) WHERE n.type = 'Country'
        RETURN n.name AS name, n""").
      async().
      futureValue.
      map {
        case CypherRow(name: String, n: NeoNode) => name -> n
        case e: Any => logger.info(s"$e");
      }.
      toList
    // TODO this isn't working!
    /* results.head("United States").props should equal
      Map("name" -> "United States",
          "type" -> "Country",
          "tag" -> "anormcyphertest",
          "code" -> "USA") */
  }

  it should "be able to parse and flatten into a tuple" in {
    val result: List[(String,Int)] =
      Cypher("""
        START n = node(*)
        WHERE n.type = 'Country' AND HAS(n.name) AND HAS(n.population)
        RETURN n.name, n.population
        ORDER BY n.name
        """
      ).asAsync(
        (str("n.name") ~ int("n.population")).map(flatten).*
      ).futureValue
    result should equal (List(("Germany",81726000), ("Monaco", 32000)))
  }
}
