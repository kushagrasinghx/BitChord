import java.net.URL; val response = URL("https://www.jiosaavn.com/api.php?__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=android").readText(); println(response.take(300))
