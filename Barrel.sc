// Pepe Barrel

Plate {
	var server;
	var <group, internalBuses;
	var positionDef, <>processDef, <>decisionDef, routeDef, routeLeafDef;
	var positionSynth, <processSynth, <decisionSynth, routeSynth;

	var <>in, <>out, <>rOut;
	var <>del; // distancia
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
			sig = DelayC.ar(sig, del, del);
			sig = FoaPush.ar(sig, angle, theta, phi);
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
			route = 0.5;
			Out.kr(kout, route);
		});

		// ruteo a las sub placas, synth invariable, regla fija.
		routeDef = SynthDef(\route, { arg a_in, k_route, out, rOut1, rOut2;
			//var sig;
			//sig = In.ar(in, 4);
			Out.ar(out, a_in); //sig);
			Out.ar(rOut1, a_in * k_route);
			Out.ar(rOut2, a_in * (1 - k_route));
		});

		// rOut [nil, nil]
		routeLeafDef = SynthDef(\routeNilNil, { arg a_in, out;
			Out.ar(out, a_in);
		});
	}

	prAddDefs {
		[positionDef, processDef,
			decisionDef, routeDef, routeLeafDef].do({ arg i;
			i.send(server);
		});
	}

	build { arg target, addAction = 'addToHead';
		server.doWhenBooted({
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
					a_in: internalBuses.processOut,
					out: out
				], decisionSynth, 'addAfter');
			}, {
				routeSynth = Synth(routeDef.name, [
					a_in: internalBuses.processOut,
					k_route: internalBuses.decisionOut,
					out: out,
					rOut1: rOut[0],
					rOut2: rOut[1],
				], decisionSynth, 'addAfter');
			});
		});
	}
}

Barrel {
	var server;
	var group, internalBuses;
	var fxDef, decoderDef;
	var fxSynth, decoderSynth;

	var <>out = 0;
	var <plates;
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
			sig = FoaDecode.ar(sig, FoaDecoderMatrix.newStereo);
			Out.ar(out, sig);
		});
	}

	prAddDefs {
		fxDef.send(server);
		decoderDef.send(server);
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

		plates = hoops.collect { arg hoop;
			var auxHoop;
			var hoopGroup = Group.new(target);

			auxHoop = quadrants.collect { arg quadrant;
				var auxQuadrant;
				var quadrantGroup = Group.new(hoopGroup);
				var lastGroup = quadrantGroup;
				var prevLevel;

				auxQuadrant = levels.collect { arg level;
					var auxLevel;
					var levelGroup = Group.new(lastGroup);
					lastGroup = levelGroup;

					auxLevel = prevLevel = (level+1).collect { arg plateNum;
						var plate, theta, phi;

						#theta, phi = this.prCalcPos(
							hoop, quadrant, level, plateNum);
						"quadrant = %\nlevel = %\nplate = %\ntheta = %\nphi = %\n".postf(quadrant, level, plateNum, theta, phi);

						plate = Plate.new(server);
						plate
						.del_(nil) // calcular
						.angle_(nil) // calcular
						.theta_(theta)
						.phi_(phi);

						// considera al bus como entrada (permite sumar)
						plate.in = Bus.audio(server, 4);

						prevLevel.postln;

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

						plate.out = internalBuses.fxIn;
						plate.build(levelGroup, 'addToHead');
					};
					[levelGroup, auxLevel]
				};
				[quadrantGroup, auxQuadrant]
			};
			[hoopGroup, auxHoop]
		};
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
			group = ParGroup.new(target, addAction);

			internalBuses.do(_.free);
			internalBuses = ();
			// considera los buses como entradas y salidas fx
			internalBuses.fxIn = Bus.audio(server, 4);
			internalBuses.fxOut = Bus.audio(server, 4);

			this.prMakeBarrel(group, levels);

			fxSynth = Synth(fxDef.name, [
				in: internalBuses.fxIn,
				out: internalBuses.fxOut,
			], group, 'addAfter'); //'addToTail'); supernova?

			decoderSynth = Synth(decoderDef.name, [
				in: internalBuses.fxOut,
				out: out,
			], fxSynth, 'addAfter'); // 'addToTail'); supernova?

			// falta la entrada de la fuente (colisión)
		});
	}

	plate { arg hoop = 0, quadrant = 0, level = 0, num = 0;
		^plates[hoop][1][quadrant][1][level][1][num];
	}

	level { arg hoop, quadrant, num;
		^plates[hoop][1][quadrant][1][num][0];
	}

	quadrant { arg hoop, num;
		^plates[hoop][1][num][0];
	}

	hoop { arg num;
		^plates[num][0];
	}
}
