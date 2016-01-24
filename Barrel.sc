// Pepe Barrel

Plate {
	var server;
	var <group, <internalBuses;
	var positionDef, <processDef, <decisionDef, routeDef, routeLeafDef;
	var positionSynth, <processSynth, <decisionSynth, routeSynth;

	var <>in, <>out, <>rOut;
	var <del; // distancia
	classvar <>maxDel = 0.5; // memoria
	var <>angle, <>theta, <>phi;

	*new { arg server;
		^super.new.init(server);
	}

	init { arg srvr;
		server = srvr;
		rOut = [nil, nil];
	}

	prMakeDefaultDefs {
		// posición de la salida en ambisonics, synth invariable
		positionDef = SynthDef(\position, { arg in, out, del, angle, theta, phi;
			var sig;
			sig = In.ar(in, 4);
			sig = DelayC.ar(sig, Plate.maxDel, del);
			sig = FoaPush.ar(sig, angle, theta, phi);
			sig = FoaDecode.ar(sig, FoaDecoderMatrix.newBtoA);
			Out.ar(out, sig);
		});

		// procesamiento y salida, synth programable
		processDef = SynthDef(\process, { arg in, out;
			var sig;
			sig = In.ar(in, 4);
			Out.ar(out, sig);
		});

		// análisis y envío, synth programable
		decisionDef = SynthDef(\decision, { arg in, kout;
			var sig, route;
			sig = In.ar(in, 4);
			route = DC.kr(0.5);
			Out.kr(kout, route);
		});

		// ruteo a las sub placas, synth invariable, regla fija.
		routeDef = SynthDef(\route, { arg in, kroute, out, rOut1, rOut2;
			in = In.ar(in, 4);
			kroute = In.kr(kroute);
			Out.ar(out, in);
			Out.ar(rOut1, in * kroute);
			Out.ar(rOut2, in * (1 - kroute));
		});

		// rOut [nil, nil]
		routeLeafDef = SynthDef(\routeNilNil, { arg in, out;
			Out.ar(out, In.ar(in, 4));
		});
	}

	prAddDefs {
		[positionDef, processDef,
			decisionDef, routeDef, routeLeafDef].do({ arg i;
			i.send(server);
		});
	}

	build { arg target, addAction;
		this.prMakeDefaultDefs(); // fix: rehace innecesariamente
		this.prAddDefs(); // fix: reenvía las synth invariables
		server.sync;

		group = Group.new(target, addAction);

		// in, out y rOut se definen externamente

		internalBuses.do(_.free);
		internalBuses = ();
		// se consideran los buses como salidas
		internalBuses.positionOut = Bus.audio(server, 4);
		internalBuses.processOut = Bus.audio(server, 4);
		internalBuses.decisionOut = Bus.control(server, 1); // fix: nil nil dummy

		positionSynth = Synth(positionDef.name, [
			in: in,
			out: internalBuses.positionOut,
			del: del,
			angle: angle,
			theta: theta,
			phi: phi
		], group, 'addToHead');

		processSynth = Synth(processDef.name, [
			in: internalBuses.positionOut,
			out: internalBuses.processOut,
		], positionSynth, 'addAfter');

		decisionSynth = Synth(decisionDef.name, [
			in: internalBuses.processOut,
			kout: internalBuses.decisionOut,
		], processSynth, 'addAfter');

		if(rOut[0].isNil && rOut[1].isNil, {
			routeSynth = Synth(routeLeafDef.name, [
				in: internalBuses.processOut,
				out: out
			], decisionSynth, 'addAfter');
		}, {
			routeSynth = Synth(routeDef.name, [
				in: internalBuses.processOut,
				kroute: internalBuses.decisionOut,
				out: out,
				rOut1: rOut[0],
				rOut2: rOut[1],
			], decisionSynth, 'addAfter');
		});
	}

	processDef_ { arg def;
		var args = def.func.argNames.as(Array);

		if(args.includes(\in) && args.includes(\out), {
			server.bind {
				processDef = def;
				processDef.send(server);
				server.sync;
				if(this.processSynth.notNil, { // notNil == running
					processSynth = Synth(processDef.name, [
						in: internalBuses.positionOut,
						out: internalBuses.processOut,
					], processSynth, 'addReplace');
				});
				// set args
			};
		}, {
			"processDef necesita de in y out".error;
			^this;
		});
	}

	decisionDef_ { arg def;
		var args = def.func.argNames.as(Array);

		if(args.includes(\in) && args.includes(\kout), {
			server.bind {
				decisionDef = def;
				decisionDef.send(server);
				server.sync;
				if(this.decisionSynth.notNil, { // notNil == running
					decisionSynth = Synth(decisionDef.name, [
						in: internalBuses.processOut,
						kout: internalBuses.decisionOut,
					], decisionSynth, 'addReplace');
					// set args
				});
			};
		}, {
			"decisionDef necesita de in y kout".error;
			^this;
		});
	}

	del_ { arg d;
		del = d;
		if(positionSynth.notNil, { positionSynth.set(\del, del) });
	}

	free {
		in.free;
		internalBuses.do(_.free);
	}
}

Barrel {
	var server;
	var group, internalBuses;
	var sourceDef, fxDef, decoderDef;
	var sourceSynth, fxSynth, decoderSynth;

	var <sourceIn;

	var <>out = 0;
	var <data;
	var <>origin = 0;
	var <>hoops = 1; // elevación
	var <>quadrants = 4; // acimut
	var <>levels = 2; // delay

	*new { arg server;
		^super.new.init(server);
	}

	init { arg srvr;
		server = srvr;
	}

	prMakeDefaultDefs {
		// ruteo de la fuente
		sourceDef = SynthDef(\monoSource, { arg in, out;
			Out.ar(out, In.ar(in, 1));
		});

		// efecto global programable
		fxDef = SynthDef(\barrelFX, { arg in, out;
			var sig;
			sig = In.ar(in, 4);
			// reverb
			Out.ar(out, sig);
		});

		// decodificador programable
		decoderDef = SynthDef(\decoder, { arg in, out = 0;
			var sig;
			sig = In.ar(in, 4);
			sig = FoaEncode.ar(sig, FoaEncoderMatrix.newAtoB);
			sig = FoaDecode.ar(sig, FoaDecoderMatrix.newStereo);
			Out.ar(out, sig);
		});
	}

	prAddDefs {
		[sourceDef, fxDef, decoderDef].do({ arg i; i.send(server) });
	}

	prCalcPos { arg hoop, quadrant, level, plateNum;
		var quadrantsOrigin = origin;
		var quadrantInc = 2pi / 4;
		var quadrantOffset = (quadrantsOrigin + (quadrantInc * quadrant));
		var levelInc = quadrantInc / (level+1);
		var plateTheta = (quadrantOffset + (levelInc * plateNum) + (levelInc / 2));
		var platePhi = 0; // TODO
		^[plateTheta.wrap2(pi), platePhi.wrap2(pi)];
	}

	prMakeBarrel { arg target;
		// estructura de barril vertical
		// en realidad es un anfiteatro con pasillos

		data = hoops.collect { arg hoop;
			var auxHoop;
			var hoopGroup = ParGroup.new(target, 'addToTail');

			auxHoop = quadrants.collect { arg quadrant;
				var auxQuadrant;
				var quadrantGroup = Group.new(hoopGroup, 'addToTail');
				var prevLevel;

				auxQuadrant = levels.collect { arg level;
					var auxLevel;
					var levelGroup = ParGroup.new(quadrantGroup, 'addToTail');

					auxLevel = prevLevel = (level+1).collect { arg plateNum;
						var plate, theta, phi;

						#theta, phi = this.prCalcPos(
							hoop, quadrant, level, plateNum);
						//[hoop, quadrant, level, plateNum, theta, phi].postln;

						plate = Plate.new(server);
						plate
						.del_(Plate.maxDel.rand) // TODO: calcular
						.angle_(pi/4) // TODO: calcular
						.theta_(theta)
						.phi_(phi);

						// considera al bus como entrada (permite sumar)
						plate.in = Bus.audio(server, 4);

						//prevLevel.postln;

						// plate.build depende de rOut
						if(prevLevel.notNil, {
							case(
								{ plateNum == 0 }, {
									//"plateNum == 0".postln;
									prevLevel[0].rOut[0] = plate.in;
								},
								{ plateNum == level }, { // seguro?
									//"plateNum == level".postln;
									prevLevel[plateNum-1].rOut[1] = plate.in;
								},
								{
									//"default".postln;
									prevLevel[plateNum-1].rOut[1] = plate.in;
									prevLevel[plateNum].rOut[0] = plate.in;
								}
							);
						});

						plate;
					};
					[levelGroup, auxLevel]
				};
				[quadrantGroup, auxQuadrant]
			};
			[hoopGroup, auxHoop]
		};

		this.platesDo({ arg plate, h, q, l, p;
			plate.out = internalBuses.fxIn;
			plate.build(data[h][1][q][1][l][0], 'addToTail');
			server.sync;
		});
	}

	build { arg hoops, quadrants, levels, target, addAction = 'addToHead';
		this.hoops = hoops ? this.hoops;
		this.quadrants = quadrants ? this.quadrants;
		this.levels = levels ? this.levels;

		server.doWhenBooted({
			this.prMakeDefaultDefs;
			this.prAddDefs;
			server.sync;

			target = target ? server;
			group = Group.new(target, addAction);

			internalBuses.do(_.free);
			internalBuses = ();
			// considera los buses como entradas y salidas fx
			internalBuses.fxIn = Bus.audio(server, 4);
			internalBuses.fxOut = Bus.audio(server, 4);

			this.prMakeBarrel(group, levels);

			sourceSynth = this.hoops.collect({ arg hoop;
				quadrants.collect({ arg quadrant;
					Synth(sourceDef.name, [
						in: sourceIn,
						out: this.plate(hoop, quadrant, 0, 0).in
					], group, 'addToHead');
				});
			});

			fxSynth = Synth(fxDef.name, [
				in: internalBuses.fxIn,
				out: internalBuses.fxOut,
			], group, 'addToTail'); //'addToTail'); supernova?

			decoderSynth = Synth(decoderDef.name, [
				in: internalBuses.fxOut,
				out: out,
			], fxSynth, 'addAfter'); // 'addToTail'); supernova?

			// falta la entrada de la fuente (colisión)
		});
	}

	sourceIn_ { arg bus;
		sourceIn = bus;
		if(sourceSynth.notNil, {
			sourceSynth.flat.do({ arg i; i.set(\in, sourceIn) });
		});
	}

	// level, quadrant y hoop podrían ser clases, pero por ahora no
	plate { arg hoop = 0, quadrant = 0, level = 0, num = 0;
		^data[hoop][1][quadrant][1][level][1][num];
	}

	level { arg hoop, quadrant, num;
		^data[hoop][1][quadrant][1][num][0];
	}

	quadrant { arg hoop, num;
		^data[hoop][1][num][0];
	}

	hoop { arg num;
		^data[num][0];
	}

	platesDo { arg func;
		hoops.do { arg h;
			quadrants.do { arg q;
				levels.do { arg l;
					(l+1).do { arg p;
						func.value(
							this.plate(h, q, l, p),
							h, q, l, p
						);
					}
				}
			}
		};
	}

	free {
		group !? _.free;
		sourceIn.free;
		internalBuses.do(_.free);
		data !? { this.platesDo(_.free) };
		group = data = internalBuses = sourceIn =
		sourceSynth = fxSynth = decoderSynth = nil;
	}
}
