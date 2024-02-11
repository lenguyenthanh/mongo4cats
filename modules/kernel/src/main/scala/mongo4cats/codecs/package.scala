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

package mongo4cats

import com.mongodb.MongoClientSettings
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry => JCodecRegistry}

package object codecs {

  type CodecRegistry = JCodecRegistry
  object CodecRegistry extends AsJava {
    val Default: CodecRegistry = merge(
      from(DocumentCodecProvider),
      from(BsonValueCodecProvider),
      MongoClientSettings.getDefaultCodecRegistry,
      from(BigDecimalCodecProvider),
      from(BigIntCodecProvider),
      from(OptionCodecProvider),
      from(MapCodecProvider),
      from(IterableCodecProvider)
    )

    def from(provider: CodecProvider, providers: CodecProvider*): CodecRegistry = fromProviders(asJava(provider :: providers.toList))

    def merge(registry: CodecRegistry, registries: CodecRegistry*): CodecRegistry = fromRegistries(asJava(registry :: registries.toList))
    def mergeWithDefault(registry: CodecRegistry): CodecRegistry                  = merge(registry, Default)
  }
}
