// test

Server.scsynth;
Server.supernova;

(
s.options.numControlBusChannels = 8192;
s.options.numAudioBusChannels = 512;
s.options.memSize = 1024 * 500;
s.reboot;
)

b = Barrel.new(s);
//b.origin = -pi/4;
b.build(quadrants: 4, levels: 4);

// placas: hoop, quadrant, level, num
b.plate(0, 0, 0, 0).theta;
b.plate(0, 0, 1, 0).theta;
b.plate(0, 0, 1, 1).theta;
b.plate(0, 1, 1, 0).theta;
b.plate(0, 1, 1, 1).theta;
b.plate(0, 2, 1, 0).theta;
b.plate(0, 2, 1, 1).theta;
b.plate(0, 3, 1, 0).theta;
b.plate(0, 3, 1, 1).theta;

// estructuras en el servidor
b.hoop(0);
b.quadrant(0, 0);
b.level(0, 0, 1)
b.plate(0, 0, 1, 0).processSynth;
b.plate(0, 0, 1, 0).group;

(
SynthDef(\test, { arg out = 0;
	//var sig = PinkNoise.ar(-6.dbamp) * Decay.ar(Impulse.ar(0.25), 0.25);
	var sig = Saw.ar(mul: -6.dbamp) * Decay.ar(Impulse.ar(0.25), 0.25);
	sig = FoaEncode.ar(sig, FoaEncoderMatrix.newOmni);
	Out.ar(out, sig)
}).add;
)

w = Synth(\test, [out: b.plate(0, 0, 0, 0).in]);
x = Synth(\test, [out: b.plate(0, 1, 0, 0).in]);
y = Synth(\test, [out: b.plate(0, 2, 0, 0).in]);
z = Synth(\test, [out: b.plate(0, 3, 0, 0).in]);
[w, x, y, z].do(_.free);

b.plate(0, 3, 3, 3).in

(
var hoops = 1, quadrants = 4, levels = 3;
hoops.do { arg h;
	quadrants.do { arg q;
		levels.do { arg l;
			(l+1).do { arg p;
				[h, q, l, p].postln;
				b.plate(h, q, l, p).in.postln;
				b.plate(h, q, l, p).rOut.postln;
			}
		};
		"".postln;
	}
};
)

(
var hoops = 1, quadrants = 4, levels = 3;
hoops.do { arg h;
	quadrants.do { arg q;
		levels.do { arg l;
			(l+1).do { arg p;
				[h, q, l, p].postln;
				b.plate(h, q, l, p).internalBuses.positionOut.postln;
				b.plate(h, q, l, p).internalBuses.processOut.postln;
			}
		};
		"".postln;
	}
};
)
