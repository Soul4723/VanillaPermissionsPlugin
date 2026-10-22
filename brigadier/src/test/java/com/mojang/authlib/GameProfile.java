package com.mojang.authlib;

import java.util.UUID;

/** Test double for authlib 7+'s GameProfile record, which has {@code id()} instead of {@code getId()}. */
public record GameProfile(UUID id, String name) {}
