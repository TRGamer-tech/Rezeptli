/*
 * Rezeptli - Bewegung. Ohne JavaScript oder bei "weniger Bewegung" bleibt die Seite eine
 * ruhige, lesbare Seite: Alles hier laeuft nur mit prefers-reduced-motion: no-preference.
 */
(function () {
  "use strict";
  if (!window.gsap || !window.ScrollTrigger) return;
  gsap.registerPlugin(ScrollTrigger);

  var mm = gsap.matchMedia();

  mm.add("(prefers-reduced-motion: no-preference)", function () {
    var root = document.documentElement;
    root.classList.add("anim");

    /* ---- Hero: Die Woerter steigen auf und "backen" - von duenn und kantig zu rund und kraeftig ---- */
    var words = gsap.utils.toArray(".hero h1 .w");
    gsap.set(words, { "--wght": 600, "--soft": 100 });
    gsap.from(words, {
      yPercent: 80, opacity: 0, rotate: 4, "--wght": 250, "--soft": 0,
      duration: 1.2, ease: "back.out(1.6)", stagger: 0.13, delay: 0.1,
    });

    /* Der Tomaten-Strich unter "steht." zeichnet sich selbst. */
    var swoosh = document.querySelector(".swoosh path");
    if (swoosh) {
      var laenge = swoosh.getTotalLength();
      gsap.fromTo(swoosh,
        { strokeDasharray: laenge, strokeDashoffset: laenge },
        { strokeDashoffset: 0, duration: 0.9, ease: "power2.out", delay: 0.75 });
    }

    gsap.from(".hero-sub, .hero-cta, .hero .eyebrow", { y: 24, opacity: 0, duration: 0.8, ease: "power3.out", stagger: 0.12, delay: 0.6 });
    gsap.from(".hero-cards .fc", { y: 160, opacity: 0, rotate: 0, duration: 1.2, ease: "back.out(1.4)", stagger: 0.15, delay: 0.3 });
    gsap.from(".mt", { scale: 0, opacity: 0, duration: 0.7, ease: "back.out(2.2)", stagger: 0.12, delay: 1.1 });
    gsap.from(".top .brand .pot", { rotate: -20, scale: 0.6, duration: 0.9, ease: "back.out(2)", delay: 0.05 });

    gsap.to(".hero-days", {
      xPercent: -35, ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: true },
    });
    gsap.to(".hero-cards", {
      yPercent: -14, ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: true },
    });

    /* ---- Geschichte: ein Handy, drei Bildschirme, an der Scroll-Leiste aufgehaengt ---- */
    var screens = gsap.utils.toArray(".screen");
    var steps = gsap.utils.toArray(".step");
    var plan = screens[0], list = screens[1], duo = screens[2];

    gsap.set([list, duo], { autoAlpha: 0, yPercent: 8, rotate: 4 });
    gsap.set(steps.slice(1), { autoAlpha: 0, y: 40 });
    gsap.set(".s-plan .day, .s-rebuild", { y: 30, opacity: 0 });
    gsap.set(".s-plan .bar i", { scaleX: 0 });
    gsap.set(".vote", { opacity: 0, scale: 0.4 });

    var cnt = document.querySelector(".cnt");
    var items = gsap.utils.toArray(".s-list .item");
    var listBar = document.querySelector(".list-bar i");
    var done = 0;
    function setDone(n) {
      done = n;
      items.forEach(function (el, i) { el.classList.toggle("done", i < n); });
      cnt.textContent = n;
      if (listBar) gsap.to(listBar, { scaleX: items.length ? n / items.length : 0, duration: 0.4, ease: "back.out(1.4)" });
    }

    /* Konfetti fuer den Treffer: jedes Stueck fliegt in seine eigene Richtung. */
    var confetti = gsap.utils.toArray(".confetti i");
    function burst() {
      confetti.forEach(function (stueck, i) {
        var winkel = (i / confetti.length) * Math.PI * 2 + gsap.utils.random(-0.3, 0.3);
        var weite = gsap.utils.random(70, 130);
        gsap.fromTo(stueck,
          { x: 0, y: 0, opacity: 1, rotate: 0 },
          {
            x: Math.cos(winkel) * weite, y: Math.sin(winkel) * weite + 40, rotate: gsap.utils.random(-360, 360),
            opacity: 0, duration: 1.1, ease: "power2.out",
          });
      });
    }

    var tl = gsap.timeline({
      defaults: { ease: "power2.inOut" },
      scrollTrigger: {
        trigger: ".story", start: "top top", end: "+=320%", pin: true, scrub: 0.6, anticipatePin: 1,
      },
    });

    tl.to(".s-plan .day", { y: 0, opacity: 1, stagger: 0.12, duration: 0.5, ease: "back.out(1.5)" }, 0)
      .to(".s-plan .bar i", { scaleX: 1, duration: 0.7 }, 0)
      .to(".s-rebuild", { y: 0, opacity: 1, duration: 0.4 }, 0.5)

      .to(steps[0], { autoAlpha: 0, y: -40, duration: 0.4 }, 1.2)
      .to(plan, { autoAlpha: 0, yPercent: -8, rotate: -4, duration: 0.6 }, 1.2)
      .to(steps[1], { autoAlpha: 1, y: 0, duration: 0.5 }, 1.45)
      .to(list, { autoAlpha: 1, yPercent: 0, rotate: 0, duration: 0.6 }, 1.45);

    for (var i = 0; i < items.length; i++) {
      (function (n) {
        tl.call(function () { setDone(n + 1 > done && tl.scrollTrigger.direction >= 0 ? n + 1 : n); }, null, 2.0 + n * 0.13);
      })(i);
    }
    /* Beim Zurueckscrollen die Haken wieder loesen */
    tl.eventCallback("onReverseComplete", function () { setDone(0); });
    tl.call(function () { if (tl.scrollTrigger.direction < 0) setDone(0); }, null, 1.9);

    tl.to(steps[1], { autoAlpha: 0, y: -40, duration: 0.4 }, 3.0)
      .to(list, { autoAlpha: 0, yPercent: -8, rotate: -4, duration: 0.6 }, 3.0)
      .to(steps[2], { autoAlpha: 1, y: 0, duration: 0.5 }, 3.25)
      .to(duo, { autoAlpha: 1, yPercent: 0, rotate: 0, duration: 0.6 }, 3.25)
      /* Der Stempel waechst, der Herz-Knopf wird gedrueckt, die Karte fliegt nach rechts. */
      .to(".vote.yes", { opacity: 1, scale: 1, duration: 0.25, ease: "back.out(2)" }, 3.95)
      .to(".db.yes", { scale: 0.82, duration: 0.12 }, 4.05)
      .to(".db.yes", { scale: 1, duration: 0.3, ease: "back.out(3)" }, 4.17)
      .to(".c-front", { x: 190, rotate: 16, opacity: 0, duration: 0.5, ease: "power2.in" }, 4.2)
      .to(".match", { opacity: 1, duration: 0.35 }, 4.5)
      .from(".match b", { scale: 0.3, rotate: -8, duration: 0.5, ease: "back.out(2.4)" }, 4.5)
      .call(function () { if (tl.scrollTrigger.direction >= 0) burst(); }, null, 4.55)
      .to({}, { duration: 0.6 });

    /* ---- Quellen: zwei Baender laufen gegeneinander ---- */
    gsap.utils.toArray(".band").forEach(function (band) {
      var dir = parseFloat(band.dataset.dir);
      var dist = function () { return Math.max(0, band.scrollWidth - window.innerWidth + 40); };
      gsap.fromTo(band, { x: function () { return dir < 0 ? 0 : -dist(); } }, {
        x: function () { return dir < 0 ? -dist() : 0; }, ease: "none",
        scrollTrigger: { trigger: band, start: "top bottom", end: "bottom top", scrub: true, invalidateOnRefresh: true },
      });
    });

    /* ---- Versprechen: Karten federn herein, die Zahl zaehlt hoch ---- */
    gsap.from(".pcard", {
      y: 70, opacity: 0, rotate: 2, duration: 0.9, ease: "back.out(1.5)", stagger: 0.15,
      scrollTrigger: { trigger: ".promise-grid", start: "top 80%" },
    });
    gsap.utils.toArray(".pcard .big").forEach(function (zahl) {
      var ziel = parseFloat(zahl.dataset.zahl || "0");
      var einheit = zahl.dataset.einheit || "";
      if (!ziel) return;
      var stand = { wert: 0 };
      gsap.to(stand, {
        wert: ziel, duration: 1.2, ease: "power2.out",
        onUpdate: function () { zahl.textContent = Math.round(stand.wert) + einheit; },
        scrollTrigger: { trigger: zahl, start: "top 85%" },
      });
    });

    /* ---- Download: der Topf springt auf, der Titel waechst ---- */
    gsap.from(".get .big-pot", {
      scale: 0.5, rotate: -12, opacity: 0, duration: 1, ease: "back.out(2)",
      scrollTrigger: { trigger: ".get", start: "top 75%" },
    });
    gsap.from(".get h2", {
      scale: 0.8, opacity: 0, duration: 1, ease: "back.out(1.6)",
      scrollTrigger: { trigger: ".get", start: "top 70%" },
    });
    gsap.utils.toArray(".sources h2, .promise h2, .faq h2").forEach(function (h) {
      gsap.from(h, { y: 50, opacity: 0, duration: 0.9, ease: "power3.out", scrollTrigger: { trigger: h, start: "top 85%" } });
    });

    return function () { root.classList.remove("anim"); };
  });
})();
