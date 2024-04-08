package com.jetbrains.rider.plugins.unreal

import com.jetbrains.rd.framework.ISerializers
import com.jetbrains.rdclient.protocol.RdStartUpSerializerRegistrar
import com.jetbrains.rider.plugins.unreal.model.UE4Library

class UE4LibraryStartUpSerializerRegistrar : RdStartUpSerializerRegistrar {
    override fun register(serializers: ISerializers) {
        UE4Library.register(serializers)
    }
}