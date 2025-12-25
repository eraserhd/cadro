{
  description = "Cadro - Manual DRO rethought";
  inputs = {
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs";
  };
  outputs = { self, nixpkgs, flake-utils }:
    (flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "34" "35" "36" ];
          buildToolsVersions = [ "34.0.0" "35.0.0" ];
          includeEmulator = false;
        };

        droserve = pkgs.stdenv.mkDerivation {
          pname = "droserve";
          version = "0.1.0";

          src = ./dev/droserve;

          nativeBuildInputs = with pkgs; [ pkg-config ];
          buildInputs = with pkgs; [ dbus dbus-glib glib readline ];

          buildPhase = ''
            gcc droserve.c profile1-iface.c -o droserve \
              -g -Wall -Werror -lreadline \
              $(pkg-config --cflags --libs dbus-1) \
              $(pkg-config --cflags --libs dbus-glib-1) \
              $(pkg-config --cflags --libs gio-2.0) \
              $(pkg-config --cflags --libs gio-unix-2.0)
          '';

          installPhase = ''
            mkdir -p $out/bin
            cp droserve $out/bin/
          '';
        };
      in {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            clojure
            nodejs_24
            jdk21

            androidComposition.androidsdk
            droserve
          ];
          ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk21}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidComposition.androidsdk}/libexec/android-sdk/build-tools/35.0.0/aapt2";
        };
    }));
}

