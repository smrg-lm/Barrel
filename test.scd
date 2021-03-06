// test

Server.scsynth;

(
Server.supernova;
s.options.maxNodes = 1024;
s.options.numControlBusChannels;
s.options.numAudioBusChannels = 4096;
s.options.memSize = 1024 * 500;
s.reboot;
)

b = Barrel.new(s);
b.sourceIn = Bus.audio(s, 1);
//b.origin = -pi/4;
b.build(hoops: 2, quadrants: 4, levels: 4);
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
var hoop = b.hoops.rand;
var quadrant = b.quadrants.rand;
var level = b.levels.rand;

// cambia al procesamiento (luego que sea solo una función)
b.plate(hoop, quadrant, 0, 0).processDef = SynthDef(\custom, { arg in, out;
	var sig = In.ar(in, 4);
	sig = Resonz.ar(sig, rrand(500, 2000), 0.01, mul: 100);
	Out.ar(out, sig);
});

// cambia la decisión
b.plate(hoop, quadrant, level, 0).decisionDef = SynthDef(\indeciso, { arg in, kout;
	var sig = In.ar(in); // hacer algo con esto...
	var control;
	control = LFNoise1.kr(1).range(0, 1);
	Out.kr(kout, control);
});
)

(
// genera ruido si hay señal pero puede servir para ajustar
b.platesDo({ arg plate, h, q, l, p;
	plate.del = Plate.maxDel.asFloat.rand;
	[plate, h, q, l, p].postln;
});
)

w.free;
b.free;
