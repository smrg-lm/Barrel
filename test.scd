// test

Server.scsynth;
Server.supernova;

(
s.options.numControlBusChannels = 8192;
s.options.numAudioBusChannels = 256;
s.boot;
)

b = Barrel.new(s);
//b.origin = -pi/4;
b.build(levels: 2);

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
b.quadrant(0, 0);
b.level(0, 0, 1)
b.plate(0, 0, 1, 0).processSynth;
b.plate(0, 0, 1, 0).group;
