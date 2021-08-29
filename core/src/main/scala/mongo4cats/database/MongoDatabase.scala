/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats.database

import cats.Monad
import cats.effect.Async
import cats.syntax.flatMap._
import com.mongodb.{MongoClientSettings, ReadConcern, ReadPreference, WriteConcern}
import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import mongo4cats.collection.{MongoCodecProvider, MongoCollection}
import mongo4cats.helpers._
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

abstract class MongoDatabase[F[_]] {
  def name: String

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoDatabase[F]

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase[F]

  def readConcern: ReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoDatabase[F]

  def codecs: CodecRegistry
  def withAddedCodec(codecRegistry: CodecRegistry): MongoDatabase[F]
  def withAddedCodec[Y](implicit classTag: ClassTag[Y], cp: MongoCodecProvider[Y]): MongoDatabase[F] = {
    val classY: Class[Y] = implicitly[ClassTag[Y]].runtimeClass.asInstanceOf[Class[Y]]
    Try(codecs.get(classY)) match {
      case Failure(_) => withAddedCodec(fromProviders(cp.get))
      case Success(_) => this
    }
  }

  def collectionNames: F[Iterable[String]]
  def createCollection(name: String, options: CreateCollectionOptions): F[Unit]
  def createCollection(name: String): F[Unit] = createCollection(name, CreateCollectionOptions())
  def getCollection(name: String): F[MongoCollection[F, Document]]
  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollection[F, T]]
  def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): F[MongoCollection[F, T]] =
    getCollection[T](name, fromRegistries(fromProviders(cp.get), MongoDatabase.DefaultCodecRegistry))
}

final private class LiveMongoDatabase[F[_]](
    private val database: JMongoDatabase
)(implicit
    val F: Async[F]
) extends MongoDatabase[F] {

  def name: String =
    database.getName

  def readPreference: ReadPreference = database.getReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoDatabase[F] =
    new LiveMongoDatabase[F](database.withReadPreference(readPreference))

  def writeConcern: WriteConcern = database.getWriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase[F] =
    new LiveMongoDatabase[F](database.withWriteConcern(writeConcert))

  def readConcern: ReadConcern = database.getReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoDatabase[F] =
    new LiveMongoDatabase[F](database.withReadConcern(readConcern))

  def codecs: CodecRegistry = database.getCodecRegistry
  def withAddedCodec(codecRegistry: CodecRegistry): MongoDatabase[F] =
    new LiveMongoDatabase[F](database.withCodecRegistry(codecRegistry))

  def getCollection(name: String): F[MongoCollection[F, Document]] =
    F.delay(database.getCollection(name).withCodecRegistry(MongoDatabase.DefaultCodecRegistry))
      .flatMap(MongoCollection.make[F, Document])

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollection[F, T]] = {
    val clazz = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    F.delay {
      database
        .getCollection[T](name, clazz)
        .withCodecRegistry(codecRegistry)
        .withDocumentClass[T](clazz)
    }.flatMap(MongoCollection.make[F, T])
  }

  def collectionNames: F[Iterable[String]] =
    database.listCollectionNames().asyncIterable[F]

  def createCollection(name: String, options: CreateCollectionOptions): F[Unit] =
    database.createCollection(name, options).asyncVoid[F]
}

object MongoDatabase {

  val DefaultCodecRegistry: CodecRegistry = MongoClientSettings.getDefaultCodecRegistry

  private[mongo4cats] def make[F[_]: Async](database: JMongoDatabase): F[MongoDatabase[F]] =
    Monad[F].pure(new LiveMongoDatabase[F](database))
}
