import os

path = r"C:\Users\Administrator\.gemini\antigravity\brain\b17e1492-ced4-4975-8b69-5864f6659afa\wireopen_interactive_app.html"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()

target1 = '<body class="bg-slate-900 text-slate-100 antialiased p-2 sm:p-4 select-none flex items-center justify-center min-h-screen">'
replace1 = '<body class="bg-slate-900 text-slate-100 antialiased p-2 sm:p-4 select-none flex items-center justify-center min-h-screen transition-colors duration-300">'

target2 = """      if (isDark) {
        win.classList.remove('light-mode');
        icon.innerText = "🌙";
        label.innerText = "深色";
      } else {
        win.classList.add('light-mode');
        icon.innerText = "☀️";
        label.innerText = "浅色";
      }"""

replace2 = """      if (isDark) {
        win.classList.remove('light-mode');
        icon.innerText = "🌙";
        label.innerText = "深色";
        document.body.classList.remove('bg-slate-100');
        document.body.classList.add('bg-slate-900');
      } else {
        win.classList.add('light-mode');
        icon.innerText = "☀️";
        label.innerText = "浅色";
        document.body.classList.remove('bg-slate-900');
        document.body.classList.add('bg-slate-100');
      }"""

c = c.replace(target1, replace1)
c = c.replace(target2, replace2)

with open(path, "w", encoding="utf-8") as f:
    f.write(c)

print("Updated b17e successfully!")
