package unfiltered.request

import org.specs._

object ParamsSpec extends Specification with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._

  class TestPlan extends unfiltered.Planify({
    case GET(UFPath("/qp", Params(params, _))) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }

    case GET(UFPath("/int", Params(params, _))) => 
      Params.Query[String](params) { q => for {
        even <- q("number") is Params.int required("missing")
      } yield ResponseString(even.get.toString) } orElse { error =>
        BadRequest ~> ResponseString(error.mkString(","))
      }

    case GET(UFPath("/even", Params(params, _))) => 
      Params.Query[String](params) { q => for {
        even <- q("number") is (Params.int, "nonnumber") is
          (_ filter(_ % 2 == 0), "odd") required "missing"
        whatever <- q("what") required("bad")
      } yield ResponseString(even.get.toString) } orElse { error =>
        BadRequest ~> ResponseString(error.mkString(","))
      }
    
    case GET(UFPath("/str", Params(params, _))) => 
      Params.Query[Int](params) { q =>
        for {
          str <- (q("param") is Params.int).optional
          req <- q("req") required(400)
        } yield ResponseString(str.get.getOrElse(0).toString)
      } orElse { error =>
        BadRequest ~> Status(error.first) ~> ResponseString("fail")
      }

    case POST(UFPath("/pp", Params(params,_))) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "Params" should {
    "extract query params" in {
      Http(host / "qp" <<? Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "extract post params" in {
      Http(host / "pp" << Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "return a number" in {
      Http(host / "int" <<? Map("number" -> "8") as_str) must_=="8"
    }
    "fail on non-number" in {
      Http.when(_ == 400)(host / "int" <<? Map("number" -> "8a") as_str) must_=="missing"
    }
    "return even number" in {
      Http(host / "even" <<? Map("number"->"8","what"->"foo") as_str) must_=="8"
    }
    "fail on non-number" in {
      Http.when(_ == 400)(
        host / "even" <<? Map("number"->"eight", "what"->"") as_str
      ) must_== "nonnumber"
    }
    "fail on odd number" in {
      Http.when(_ == 400)(host / "even" <<? Map("number" -> "7") as_str) must_=="odd,bad"
    }
    "fail on not present" in {
      Http.when(_ == 400)(host / "even" as_str) must_=="missing,bad"
    }
    "return zero if param not an int" in {
      Http(host / "str" <<? Map("param"->"hi","req"->"whew") as_str) must_=="0"
    }
  }
}