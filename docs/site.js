// CurrenciX website: the working pieces of the app on the page. Everything
// is in the HTML already; this only makes it respond. No requests leave the
// page.
(() => {
  "use strict";

  const money = (value, digits = 2) =>
    Number.isFinite(value)
      ? value.toLocaleString("en-US", { minimumFractionDigits: digits, maximumFractionDigits: digits })
      : "—";
  const parse = (text) => Number.parseFloat(String(text).replace(/,/g, ""));

  // ── Sections arrive: each marked part rises once, in order ──
  const reveal = document.querySelectorAll("[data-reveal]");
  if ("IntersectionObserver" in window && reveal.length) {
    const seen = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add("in");
          seen.unobserve(entry.target);
        }
      }
    }, { rootMargin: "0px 0px -8% 0px" });
    reveal.forEach((el) => seen.observe(el));
  } else {
    reveal.forEach((el) => el.classList.add("in"));
  }

  // ── The receipt: a price, an example rate and a card fee ──
  const receipt = document.querySelector("[data-receipt]");
  if (receipt) {
    const price = receipt.querySelector("#price");
    const rate = receipt.querySelector("#rate");
    const swap = receipt.querySelector(".swap");
    const fees = receipt.querySelectorAll(".fees button");
    const stamp = receipt.querySelector(".stamp");
    const out = (name) => receipt.querySelector(`[data-out="${name}"]`);
    const state = { fee: 2.5, reversed: false };
    const SIDES = { ILS: "₪", USD: "$" };

    const render = () => {
      const from = state.reversed ? "USD" : "ILS";
      const to = state.reversed ? "ILS" : "USD";
      const usdInIls = parse(rate.value);
      const amount = parse(price.value);
      // Rounded to the cent before adding, so the receipt's lines add up.
      const cents = (v) => Math.round(v * 100) / 100;
      const mid = cents(state.reversed ? amount * usdInIls : amount / usdInIls);
      const fee = cents(mid * (state.fee / 100));
      receipt.querySelector("[data-from]").textContent = from;
      receipt.querySelector("[data-to]").textContent = to;
      receipt.querySelector("[data-symbol]").textContent = SIDES[from];
      out("mid").textContent = `${SIDES[to]} ${money(mid)}`;
      out("fee").textContent = `+ ${SIDES[to]} ${money(fee)}`;
      out("pay").textContent = `${SIDES[to]} ${money(mid + fee)}`;
      stamp.hidden = state.fee === 0;
      stamp.querySelector("b").textContent = `+${state.fee}%`;
    };

    price.addEventListener("input", render);
    rate.addEventListener("input", render);
    swap.addEventListener("click", () => {
      state.reversed = !state.reversed;
      swap.classList.toggle("turned", state.reversed);
      render();
    });
    fees.forEach((button) =>
      button.addEventListener("click", () => {
        state.fee = Number(button.dataset.fee);
        fees.forEach((b) => b.setAttribute("aria-pressed", String(b === button)));
        stamp.classList.remove("thud");
        void stamp.offsetWidth; // restart the stamp's landing
        stamp.classList.add("thud");
        render();
      }),
    );
    render();
  }

  // ── The converter: a working keypad ──
  const converter = document.querySelector("[data-converter]");
  if (converter) {
    const RATE = 3.7; // the page's example rate: 1 USD = 3.70 ILS
    const FEE = 1.75; // the example card
    const expr = converter.querySelector("[data-expr]");
    const from = converter.querySelector("[data-amount]");
    const to = converter.querySelector("[data-result]");
    const pay = converter.querySelector("[data-true]");
    let input = "64.86";

    // + − × ÷, brackets and percent, worked out left to right with precedence.
    const evaluate = (text) => {
      const tokens = text.match(/\d*\.?\d+%?|[-+×÷()]/g) || [];
      let i = 0;
      const peek = () => tokens[i];
      const number = () => {
        const t = tokens[i++];
        if (t === "(") { const v = sum(); if (peek() === ")") i++; return v; }
        if (t === "-") return -number();
        if (t === undefined) return NaN;
        return t.endsWith("%") ? parse(t) / 100 : parse(t);
      };
      const product = () => {
        let v = number();
        while (peek() === "×" || peek() === "÷") v = tokens[i++] === "×" ? v * number() : v / number();
        return v;
      };
      const sum = () => {
        let v = product();
        while (peek() === "+" || peek() === "-") v = tokens[i++] === "+" ? v + product() : v - product();
        return v;
      };
      return sum();
    };

    const show = () => {
      const isExpression = /[-+×÷()%]/.test(input);
      const value = evaluate(input);
      expr.textContent = isExpression ? input.replace(/-/g, "−") : "";
      from.textContent = `$ ${input ? money(value) : "0"}`;
      to.textContent = `₪ ${money(value * RATE)}`;
      pay.textContent = `₪ ${money(value * RATE * (1 + FEE / 100))}`;
    };

    converter.querySelectorAll(".keypad button").forEach((key) =>
      key.addEventListener("click", () => {
        const k = key.dataset.key;
        if (k === "C") input = "";
        else if (k === "back") input = input.slice(0, -1);
        else if (k === "=") { const v = evaluate(input); input = Number.isFinite(v) ? String(Math.round(v * 100) / 100) : ""; }
        else if (k === "()") input += (input.split("(").length > input.split(")").length && /[\d)%]$/.test(input)) ? ")" : "(";
        else input += k;
        show();
      }),
    );
    show();

    // Each note sits level with the part it explains, pushed down if the
    // one above it runs long (only where the notes stand in the margins).
    const place = () => {
      document.querySelectorAll(".plate-notes").forEach((side) => {
        const notes = [...side.querySelectorAll("[data-note]")];
        if (!notes.length || getComputedStyle(notes[0]).position !== "absolute") return;
        const title = side.querySelector(".plate-title");
        let floor = title ? title.offsetHeight + 24 : 0;
        for (const note of notes) {
          const part = converter.querySelector(`[data-part="${note.dataset.note}"]`);
          const top = Math.max(part ? part.getBoundingClientRect().top - converter.getBoundingClientRect().top : 0, floor);
          note.style.top = `${top}px`;
          floor = top + note.offsetHeight + 20;
        }
      });
    };
    place();
    window.addEventListener("resize", place);
    document.fonts?.ready.then(place);

    // A note lights its part of the panel while it's pointed at or focused.
    document.querySelectorAll("[data-note]").forEach((note) => {
      const part = converter.querySelector(`[data-part="${note.dataset.note}"]`);
      const on = (lit) => { part?.classList.toggle("lit", lit); note.classList.toggle("on", lit); };
      note.addEventListener("pointerenter", () => on(true));
      note.addEventListener("pointerleave", () => on(false));
      note.addEventListener("focusin", () => on(true));
      note.addEventListener("focusout", () => on(false));
    });
  }

  // ── The boards: one row open at a time ──
  document.querySelectorAll("[data-board]").forEach((board) => {
    board.querySelectorAll("button").forEach((button) =>
      button.addEventListener("click", () => {
        board.querySelectorAll("li").forEach((li) => {
          const open = li === button.parentElement;
          li.classList.toggle("open", open);
          li.querySelector("button").setAttribute("aria-expanded", String(open));
        });
      }),
    );
  });
})();
