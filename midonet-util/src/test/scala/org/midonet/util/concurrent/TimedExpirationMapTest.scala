/*
 * Copyright (c) 2014 Midokura SARL, All Rights Reserved.
 */

package org.midonet.util.concurrent

import java.util.concurrent.{ThreadLocalRandom, CountDownLatch}

import scala.concurrent.duration._

import akka.event.NoLogging

import org.junit.runner.RunWith
import org.scalatest.{OneInstancePerTest, Matchers, FeatureSpec}
import org.scalatest.junit.JUnitRunner

import org.midonet.util.collection.Reducer

@RunWith(classOf[JUnitRunner])
class TimedExpirationMapTest extends FeatureSpec
                             with Matchers
                             with OneInstancePerTest {

    val map = new TimedExpirationMap[String, String](NoLogging, _ => 0 millis)

    feature("Normal operations") {
        scenario("putAndRef") {
            var prev = map.putAndRef("A", "X")
            prev should be (null)
            map get "A" should be ("X")
            map refCount "A" should be (1)

            prev = map.putAndRef("A", "Y")
            prev should be ("X")
            map get "A" should be ("Y")
            map refCount "A" should be (2)
        }

        scenario("putIfAbsentAndRef") {
            var prev = map.putIfAbsentAndRef("A", "X")
            prev should be (null)
            map get "A" should be ("X")
            map refCount "A" should be (1)

            prev = map.putIfAbsentAndRef("A", "Y")
            prev should be ("X")
            map get "A" should be ("X")
            map refCount "A" should be (2)
        }

        scenario("fold") {
            map.putIfAbsentAndRef("A", "X")
            map.putIfAbsentAndRef("B", "Y")
            map.putIfAbsentAndRef("C", "Z")

            map.fold("", new Reducer[String, String, String]() {
                override def apply(acc: String, key: String,
                                   value: String): String = {
                    acc + key + value
                }
            }).sorted should be ("ABCXYZ")
        }

        scenario("ref") {
            map.ref("A") should be (null)
            map.putIfAbsentAndRef("A", "X")
            map.ref("A") should be ("X")
            map get "A" should be ("X")
            map refCount "A" should be (2)
        }

        scenario("unref") {
            map.unref("A", 0) should be (null)
            map.putIfAbsentAndRef("A", "X")
            map.ref("A") should be ("X")
            map refCount "A" should be (2)
            map.unref("A", 0) should be ("X")
            map refCount "A" should be (1)
            map.unref("A", 0) should be ("X")
            map refCount "A" should be (0)
        }

        scenario("obliterateIdleEntries blocks operations on the same key") {
            map.putAndRef("A", "X")
            map.unref("A", 0) should be ("X")

            val outerlLatch = new CountDownLatch(1)

            map.obliterateIdleEntries(1, "", new Reducer[String, String, String]() {
                override def apply(acc: String, key: String,
                                   value: String): String = {
                    var retries = 100
                    while (retries > 0) {
                        map.ref("A") should be (null)
                        retries -= 1
                    }

                    val innerlLatch = new CountDownLatch(1)
                    new Thread() {
                        override def run() {
                            innerlLatch.countDown()
                            map.putAndRef("A", "Y")
                            outerlLatch.countDown()
                        }
                    }.start()

                    innerlLatch.await()

                    retries = 500
                    while (retries > 0) {
                        map.get("A") should be (null)
                        retries -= 1
                    }

                    acc + key + value
                }
            }) should be ("AX")

            outerlLatch.await()
            map.get("A") should be ("Y")
        }
    }

    feature("Correctness test") {
        scenario("control for reference count") {
            val keys = (0 to 5000) map { _.toString } toArray
            val operations = 5000000

            val threads = new Array[Thread](2)
            val refs = new Array[Int](keys.length)
            threads(0) = new Thread() {
                override def run() {
                    val rand = ThreadLocalRandom.current()
                    var i = 0
                    while (i < operations) {
                        val index = rand.nextInt(0, keys.length)
                        val key = keys(index)

                        map.putAndRef(key, rand.nextInt().toString)
                        refs(index) += 1
                        // Can't control for the value because it might be unrefed
                        // and removed by the other thread; since we can't guarantee
                        // a happens-before relationship with unref, we also can't
                        // guarantee one with the obliterate operation.

                        i += 1
                    }
                }
            }

            val unrefs = new Array[Int](keys.length)
            threads(1) = new Thread()  {
                override def run() {
                    val rand = ThreadLocalRandom.current()
                    var i = 0
                    while (i < operations) {
                        val index = rand.nextInt(0, keys.length)
                        val key = keys(index)

                        if (rand.nextInt(10) < 7) {
                            if (map.getRefCount(key) > 0) {
                                map.unref(key, 0)
                                unrefs(index) += 1
                            }
                        } else {
                            map.obliterateIdleEntries(1)
                        }

                        i += 1
                    }
                }
            }

            threads foreach (_.start())
            threads foreach (_.join())

            val results = (refs, unrefs).zipped map (_ - _)
            (0 until keys.length) foreach { i =>
                map.getRefCount(i.toString) should be (results(i))
            }
        }
    }
}
