// test

Server.scsynth;

(
Server.supernova;
s.options.maxSynthDefs;
s.options.maxNodes;
s.options.numControlBusChannels;
s.options.numAudioBusChannels = 1024;
s.options.memSize = 1024 * 500;
s.reboot;
)

b = Barrel.new(s);
b.sourceIn = Bus.audio(s, 1);
//b.origin = -pi/4;
b.build(quadrants: 4, levels: 4);
// levels = 4 con scsynth no funciona en mi máquina,
// con supernova si.
// Si no hay capacidad de cómputo bajar los niveles
// o la cantidad de cuadrantes en su defecto.

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
	var sig = PinkNoise.ar(-6.dbamp) * Decay.ar(Impulse.ar(0.25), 0.25);
	//var sig = Saw.ar(mul: -6.dbamp) * Decay.ar(Impulse.ar(0.25), 0.25);
	//var sig = SinOsc.ar(mul: -6.dbamp) * Decay.ar(Impulse.ar(0.25), 0.25);
	//var sig = SinOsc.ar(mul: -12.dbamp);
	sig = FoaEncode.ar(sig, FoaEncoderMatrix.newOmni);
	Out.ar(out, sig)
}).add;
)

w = Synth(\test, [out: b.sourceIn]);

(
// Ejecutar varias veces, valores aleatorios
var quadrant = b.quadrants.rand;
var level = b.levels.rand;

// cambia al procesamiento (luego que sea solo una función)
b.plate(0, quadrant, 0, 0).processDef = SynthDef(\custom, { arg in, out;
	var sig = In.ar(in, 4);
	sig = Resonz.ar(sig, rrand(500, 2000), 0.01, mul: 100);
	Out.ar(out, sig);
});

// cambia la decisión
b.plate(0, quadrant, level, 0).decisionDef = SynthDef(\indeciso, { arg in, kout;
	var sig = In.ar(in); // hacer algo con esto...
	var control;
	control = LFNoise1.kr(1).range(0, 1);
	Out.kr(kout, control);
});
)

w.free;

// recorridos
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
