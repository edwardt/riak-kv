package com.plasmaconduit.riak

import com.plasmaconduit.json._
import com.plasmaconduit.rxnettyscala.transformers.StringDecoder
import com.plasmaconduit.rxnettyscala.{HttpClientResponse, HttpClient}
import com.plasmaconduit.rxnettyscala.http.{NoContent, NotFound, Ok}
import com.plasmaconduit.url.URL
import io.netty.buffer.{Unpooled, ByteBuf}
import rx.lang.scala.Observable

final case class RiakKvClient(server: URL) {

  def getBucketType(bucketType: String): RiakBucketType = {
    RiakBucketType(server, bucketType)
  }

}

final case class RiakBucketType(server: URL, bucketType: String) {

  def getBucket(bucket: String): RiakBucket = {
    RiakBucket(server, bucketType, bucket)
  }

}

final case class RiakBucket(server: URL, bucketType: String, bucket: String) {

  def get(key: String): Observable[RiakValue[ByteBuf]] = {
    val location = RiakLocation(server, bucketType, bucket, key)
    HttpClient
      .get(location.url)
      .flatMap(response => response.status match {
      case Ok => response
        .getContent
        .map(n => Unpooled.copiedBuffer(n))
        .reduce((m, n) => Unpooled.wrappedBuffer(m, n))
        .map(body => RiakValue[ByteBuf](location, response, body))
      case NotFound => Observable.empty
      case _ => Observable.error(RiakMultiValueResponse)
    })
  }

  def getAsString(key: String): Observable[RiakValue[String]] = {
    get(key)
      .map(value =>
      value.copy(body = StringDecoder()(value.body))
      )
  }

  def getAsJson(key: String): Observable[RiakValue[JsValue]] = {
    getAsString(key).flatMap(value => JsonParser.parse(value.body) match {
      case Some(json) => Observable.just(value.copy(body = json))
      case None       => Observable.error(RiakFailedParsingAsJson)
    })
  }

  def exists(key: String): Observable[Boolean] = {
    get(key).nonEmpty
  }

  def set(key: String, contentType: String, value: ByteBuf): Observable[Unit] = {
    val location = RiakLocation(server, bucketType, bucket, key)
    HttpClient.put(
      url     = location.url,
      headers = Seq("Content-Type" -> contentType),
      body    = Some(value.array())
    ).map(_ => Unit)
  }

  def setAsString(key: String, value: String): Observable[Unit] = {
    set(key, "text/plain", Unpooled.wrappedBuffer(value.getBytes("UTF-8")))
  }

  def setAsJson(key: String, value: JsValue): Observable[Unit] = {
    set(key, "application/json", Unpooled.wrappedBuffer(value.toString.getBytes("UTF-8")))
  }

  def update(value: RiakValue[ByteBuf]): Observable[Unit] = {
    HttpClient.put(
      url     = value.location.url,
      headers = Seq(
        value.contentType.map(n => "Content-Type"  -> n),
        value.vectorClock.map(n => "X-Riak-Vclock" -> n)
      ).flatten,
      body = Some(value.body.array())
    ).map(_ => Unit)
  }

  def updateAsString(value: RiakValue[String]): Observable[Unit] = {
    update(RiakValue[ByteBuf](
      location = value.location,
      response = value.response,
      body     = Unpooled.wrappedBuffer(value.body.getBytes("UTF-8"))
    ))
  }

  def updateAsJson(value: RiakValue[JsValue]): Observable[Unit] = {
    updateAsString(RiakValue[String](
      location = value.location,
      response = value.response,
      body     = value.toString
    ))
  }

  def delete(key: String): Observable[Boolean] = {
    HttpClient
      .delete(server.setPath(s"/types/$bucketType/buckets/$bucket/keys/$key"))
      .map(response => response.status match {
      case Ok        => true
      case NoContent => true
      case NotFound  => true
      case _         => false
    })
  }

}

object RiakMultiValueResponse extends Throwable
object RiakFailedParsingAsJson extends Throwable

final case class RiakLocation(server: URL, bucketType: String, bucket: String, key: String) {

  def url = server.setPath(s"/types/$bucketType/buckets/$bucket/keys/$key")

}

final case class RiakValue[A](location: RiakLocation, response: HttpClientResponse, body: A) {

  def setValue(newValue: A) = copy(body = newValue)

  def contentType = response.headers.get("Content-Type")

  def vectorClock = response.headers.get("X-Riak-Vclock")

  def etag = response.headers.get("Etag")

}