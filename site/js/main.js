/* Rezeptli - Bewegung. Ohne JavaScript oder bei "weniger Bewegung" bleibt die Seite eine ruhige, lesbare Seite. */
(function () {
  "use strict";
  if (!window.gsap || !window.ScrollTrigger) return;
  gsap.registerPlugin(ScrollTrigger);

  var mm = gsap.matchMedia();

  mm.add("(prefers-reduced-motion: no-preference)", function () {
    var root = document.documentElement;
    root.classList.add("anim");

    /* ---- Hero: Titel wird breit, Teller fächern sich auf ---- */
    var words = gsap.utils.toArray(".hero h1 .w");
    gsap.from(words, {
      yPercent: 70, opacity: 0, fontStretch: "75%", rotate: 3,
      duration: 1.1, ease: "power4.out", stagger: 0.14, delay: 0.1,
    });
    gsap.from(".hero-sub, .hero-cta, .hero .eyebrow", { y: 24, opacity: 0, duration: 0.8, ease: "power3.out", stagger: 0.12, delay: 0.7 });
    gsap.from(".hero-cards .fc", { y: 140, opacity: 0, rotate: 0, duration: 1.2, ease: "back.out(1.3)", stagger: 0.15, delay: 0.3 });
    gsap.to(".hero-days", {
      xPercent: -35, ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: true },
    });
    gsap.to(".hero-cards", {
      yPercent: -14, ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: true },
    });

    /* ---- Geschichte: ein Handy, drei Bildschirme, an der Scroll-Leiste aufgehängt ---- */
    var screens = gsap.utils.toArray(".screen");
    var steps = gsap.utils.toArray(".step");
    var plan = screens[0], list = screens[1], duo = screens[2];

    gsap.set([list, duo], { autoAlpha: 0, yPercent: 8, rotate: 4 });
    gsap.set(steps.slice(1), { autoAlpha: 0, y: 40 });
    gsap.set(".s-plan .day, .s-rebuild", { y: 30, opacity: 0 });
    gsap.set(".s-plan .bar i", { scaleX: 0 });
    gsap.set(".vote", { opacity: 0 });

    var cnt = document.querySelector(".cnt");
    var items = gsap.utils.toArray(".s-list .item");
    var done = 0;
    function setDone(n) {
      done = n;
      items.forEach(function (el, i) { el.classList.toggle("done", i < n); });
      cnt.textContent = n;
    }

    var tl = gsap.timeline({
      defaults: { ease: "power2.inOut" },
      scrollTrigger: {
        trigger: ".story", start: "top top", end: "+=320%", pin: true, scrub: 0.6, anticipatePin: 1,
      },
    });

    tl.to(".s-plan .day", { y: 0, opacity: 1, stagger: 0.12, duration: 0.5 }, 0)
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
    /* Beim Zurückscrollen die Haken wieder lösen */
    tl.eventCallback("onReverseComplete", function () { setDone(0); });
    tl.call(function () { if (tl.scrollTrigger.direction < 0) setDone(0); }, null, 1.9);

    tl.to(steps[1], { autoAlpha: 0, y: -40, duration: 0.4 }, 3.0)
      .to(list, { autoAlpha: 0, yPercent: -8, rotate: -4, duration: 0.6 }, 3.0)
      .to(steps[2], { autoAlpha: 1, y: 0, duration: 0.5 }, 3.25)
      .to(duo, { autoAlpha: 1, yPercent: 0, rotate: 0, duration: 0.6 }, 3.25)
      .to(".vote.yes", { opacity: 1, duration: 0.2 }, 4.0)
      .to(".c-front", { x: 190, rotate: 16, opacity: 0, duration: 0.5 }, 4.2)
      .to(".match", { opacity: 1, duration: 0.4 }, 4.5)
      .from(".match b", { scale: 0.3, duration: 0.5, ease: "back.out(2)" }, 4.5)
      .to({}, { duration: 0.6 });

    /* ---- Quellen: zwei Bänder laufen gegeneinander ---- */
    gsap.utils.toArray(".band").forEach(function (band) {
      var dir = parseFloat(band.dataset.dir);
      var dist = function () { return Math.max(0, band.scrollWidth - window.innerWidth + 40); };
      gsap.fromTo(band, { x: function () { return dir < 0 ? 0 : -dist(); } }, {
        x: function () { return dir < 0 ? -dist() : 0; }, ease: "none",
        scrollTrigger: { trigger: band, start: "top bottom", end: "bottom top", scrub: true, invalidateOnRefresh: true },
      });
    });

    /* ---- Versprechen & Download ---- */
    gsap.from(".pcard", {
      y: 70, opacity: 0, duration: 0.8, ease: "power3.out", stagger: 0.15,
      scrollTrigger: { trigger: ".promise-grid", start: "top 80%" },
    });
    gsap.from(".get h2", {
      scale: 0.8, opacity: 0, duration: 1, ease: "power3.out",
      scrollTrigger: { trigger: ".get", start: "top 70%" },
    });
    gsap.utils.toArray(".sources h2, .promise h2").forEach(function (h) {
      gsap.from(h, { y: 50, opacity: 0, duration: 0.9, ease: "power3.out", scrollTrigger: { trigger: h, start: "top 85%" } });
    });

    return function () { root.classList.remove("anim"); };
  });
})();
